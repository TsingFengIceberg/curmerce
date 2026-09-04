package cn.iocoder.yudao.curmerce.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores small, privacy-preserving answer feedback records. Prompts, model
 * answers and raw bearer tokens are intentionally never accepted or stored.
 */
@Component
public class AgentFeedbackRecorder {
    private static final String PREFIX = "curmerce:agent:feedback:v2";
    private static final Duration RETENTION = Duration.ofDays(90);
    private static final java.util.Set<String> CATEGORIES = java.util.Set.of("answer", "sources", "tool", "safety");

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MeterRegistry metrics;
    private final Map<String, Feedback> local = new ConcurrentHashMap<>();

    public AgentFeedbackRecorder(MeterRegistry metrics) {
        this((StringRedisTemplate) null, (ObjectMapper) null, metrics);
    }

    @Autowired
    public AgentFeedbackRecorder(ObjectProvider<StringRedisTemplate> redisProvider, ObjectMapper objectMapper,
                                 MeterRegistry metrics) {
        this(redisProvider.getIfAvailable(), objectMapper, metrics);
    }

    AgentFeedbackRecorder(StringRedisTemplate redis, ObjectMapper objectMapper, MeterRegistry metrics) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    /** Upserts the caller's reaction for one assistant message. */
    public Feedback record(Long userId, String conversationId, String messageId, boolean helpful, String category) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("反馈用户无效");
        String conversation = requireId(conversationId, "会话编号");
        String message = requireId(messageId, "消息编号");
        String safeCategory = normalizeCategory(category);
        String principal = AgentPrincipalHasher.hash(String.valueOf(userId));
        Feedback value = new Feedback(Instant.now(), principal, digest(conversation), digest(message), helpful, safeCategory);
        String key = AgentRequestContext.key(PREFIX) + ":" + principal + ":" + value.messageHash();
        local.put(key, value);
        if (redis != null && objectMapper != null) try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), RETENTION);
        } catch (Exception ex) {
            metrics.counter("curmerce.agent.feedback", "result", "local-fallback", "helpful", String.valueOf(helpful),
                    "category", safeCategory).increment();
            return value;
        }
        metrics.counter("curmerce.agent.feedback", "result", "accepted", "helpful", String.valueOf(helpful),
                "category", safeCategory).increment();
        return value;
    }

    private static String requireId(String value, String label) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException(label + "格式无效");
        }
        return value;
    }

    private static String normalizeCategory(String value) {
        String normalized = value == null || value.isBlank() ? "answer" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!CATEGORIES.contains(normalized)) throw new IllegalArgumentException("反馈分类无效");
        return normalized;
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(32);
            for (int index = 0; index < 16; index++) result.append(String.format("%02x", bytes[index]));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public record Feedback(Instant at, String principalHash, String conversationHash,
                           String messageHash, boolean helpful, String category) { }
}

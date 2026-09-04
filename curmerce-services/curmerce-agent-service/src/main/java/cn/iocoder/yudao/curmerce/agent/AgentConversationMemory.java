package cn.iocoder.yudao.curmerce.agent;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Base64;
import java.time.Duration;
import java.util.Set;

/** Bounded, user-scoped conversation memory; replaceable with Redis/SQL persistence. */
@Component
public class AgentConversationMemory {
    private static final int MAX_MESSAGES = 12;
    private final Map<String, Deque<Message>> conversations = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;

    public AgentConversationMemory() { this.redis = null; }

    @Autowired
    public AgentConversationMemory(org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> provider) {
        this.redis = provider.getIfAvailable();
    }

    public void append(String conversationId, String role, String content) {
        if (conversationId == null || conversationId.isBlank() || content == null || content.isBlank()) return;
        String safeConversationId = normalizeConversationId(conversationId);
        String safeRole = normalizeRole(role);
        String safeContent = AgentInputPolicy.redactSecrets(content.trim());
        if (safeContent.length() > 8000) safeContent = safeContent.substring(0, 8000);
        Deque<Message> messages = conversations.computeIfAbsent(safeConversationId, ignored -> new ArrayDeque<>());
        synchronized (messages) {
            messages.addLast(new Message(safeRole, safeContent, Instant.now()));
            while (messages.size() > MAX_MESSAGES) messages.removeFirst();
        }
        if (redis != null) {
            try {
                String value = Base64.getUrlEncoder().withoutPadding().encodeToString(safeRole.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(safeContent.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "." + Instant.now();
                String key = key(safeConversationId);
                redis.opsForList().rightPush(key, value); redis.opsForList().trim(key, -MAX_MESSAGES, -1); redis.expire(key, Duration.ofDays(7));
            } catch (RuntimeException ignored) { /* fall back to local memory while Redis recovers */ }
        }
    }

    public java.util.List<Message> history(String conversationId) {
        String safeConversationId = normalizeConversationId(conversationId);
        Deque<Message> messages = conversations.get(safeConversationId);
        if (redis != null) {
            try {
                var values = redis.opsForList().range(key(safeConversationId), 0, -1);
                if (values != null && !values.isEmpty()) return values.stream().map(this::decode).toList();
            } catch (RuntimeException ignored) { }
        }
        if (messages == null) return java.util.List.of();
        synchronized (messages) { return java.util.List.copyOf(messages); }
    }

    public void clear(String conversationId) { String safe = normalizeConversationId(conversationId); conversations.remove(safe); if (redis != null) try { redis.delete(key(safe)); } catch (RuntimeException ignored) { } }
    private String key(String conversationId) { return AgentRequestContext.key("curmerce:agent:conversation:v1") + ":" + conversationId; }
    private static String normalizeConversationId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > 128 || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("会话编号格式无效");
        }
        return normalized;
    }
    private static String normalizeRole(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("user", "assistant", "system", "tool").contains(normalized)) {
            throw new IllegalArgumentException("会话角色无效");
        }
        return normalized;
    }
    private Message decode(String value) {
        try {
            String[] parts = value.split("\\.", 3);
            return new Message(new String(Base64.getUrlDecoder().decode(parts[0]), java.nio.charset.StandardCharsets.UTF_8),
                    new String(Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8), Instant.parse(parts[2]));
        } catch (RuntimeException ex) { return new Message("system", "", Instant.now()); }
    }
    public record Message(String role, String content, Instant at) { }
}

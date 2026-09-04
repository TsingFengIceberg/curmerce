package cn.iocoder.yudao.curmerce.agent;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

/** One-time user-bound confirmation tokens for future sensitive Agent tools. */
@Service
public class AgentConfirmationService {
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int MAX_ACTIVE_TOKENS = 10_000;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Confirmation> tokens = new ConcurrentHashMap<>();
    private final AgentCoreClient coreClient;
    private final StringRedisTemplate redis;

    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('DEL', KEYS[1])
            redis.call('ZREM', KEYS[2], ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> ISSUE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[6]) then return 0 end
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[4])
            redis.call('SET', KEYS[2], ARGV[3], 'EX', ARGV[5])
            redis.call('EXPIRE', KEYS[1], tonumber(ARGV[5]) + 60)
            return 1
            """, Long.class);

    public AgentConfirmationService(AgentCoreClient coreClient) { this.coreClient = coreClient; this.redis = null; }

    @Autowired
    public AgentConfirmationService(AgentCoreClient coreClient,
                                    org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> provider) {
        this.coreClient = coreClient; this.redis = provider.getIfAvailable();
    }

    public Issued issue(String authorization, String action, String target) {
        Long userId = coreClient.authenticate(authorization);
        String safeAction = normalize(action, "确认动作");
        String safeTarget = normalize(target, "确认目标");
        cleanupExpired();
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random.generateSeed(24));
        Instant expiresAt = Instant.now().plus(TTL);
        Confirmation confirmation = new Confirmation(userId, safeAction, safeTarget, expiresAt);
        if (redis != null) {
            try {
                Long accepted = redis.execute(ISSUE_SCRIPT, List.of(activeKey(), redisKey(token)),
                        String.valueOf(Instant.now().getEpochSecond()), String.valueOf(expiresAt.getEpochSecond()),
                        encode(confirmation), token, String.valueOf(TTL.toSeconds()), String.valueOf(MAX_ACTIVE_TOKENS));
                if (Long.valueOf(1L).equals(accepted)) return new Issued(token, TTL.toSeconds());
                throw new RedisQuotaExceededException();
            } catch (RedisQuotaExceededException ex) {
                throw new IllegalStateException("确认令牌服务暂时繁忙");
            } catch (RuntimeException ex) {
                // A configured Redis store is the cross-instance authority.
                // Falling back to process-local tokens during an outage would
                // make confirmation semantics differ between instances.
                throw new IllegalStateException("确认令牌服务暂时不可用", ex);
            }
        }
        if (tokens.size() >= MAX_ACTIVE_TOKENS) throw new IllegalStateException("确认令牌服务暂时繁忙");
        tokens.put(token, confirmation);
        return new Issued(token, TTL.toSeconds());
    }

    public void consume(String authorization, String token, String action, String target) {
        Long userId = coreClient.authenticate(authorization);
        if (token == null || token.isBlank()) {
            throw new AgentAuthorizationException("确认令牌无效、过期或不匹配");
        }
        String safeAction = normalize(action, "确认动作");
        String safeTarget = normalize(target, "确认目标");
        if (redis != null) {
            try {
                String expected = encode(new Confirmation(userId, safeAction, safeTarget, Instant.EPOCH));
                Long consumed = redis.execute(CONSUME_SCRIPT, List.of(redisKey(token), activeKey()), expected, token);
                if (Long.valueOf(1L).equals(consumed)) return;
                throw new AgentAuthorizationException("确认令牌无效、过期或不匹配");
            } catch (AgentAuthorizationException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new AgentAuthorizationException("确认令牌服务暂时不可用");
            }
        }
        Confirmation confirmation = tokens.get(token);
        if (confirmation == null || confirmation.expiresAt().isBefore(Instant.now())
                || !confirmation.userId().equals(userId)
                || !confirmation.action().equals(safeAction) || !confirmation.target().equals(safeTarget)) {
            throw new AgentAuthorizationException("确认令牌无效、过期或不匹配");
        }
        // Consume only after all bindings match; an unrelated caller must not be
        // able to invalidate a valid token belonging to another user.
        if (!tokens.remove(token, confirmation)) {
            throw new AgentAuthorizationException("确认令牌已被消费");
        }
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        tokens.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String redisKey(String token) { return AgentRequestContext.key("curmerce:agent:confirmation:v1") + ":" + token; }
    private String activeKey() { return AgentRequestContext.key("curmerce:agent:confirmation:v1:active"); }
    private String encode(Confirmation value) {
        return value.userId() + "|" + value.action() + "|" + value.target();
    }

    private static String normalize(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException(label + "格式无效");
        }
        return normalized;
    }

    public static class AgentAuthorizationException extends RuntimeException {
        public AgentAuthorizationException(String message) { super(message); }
    }
    private static class RedisQuotaExceededException extends RuntimeException { }
    private record Confirmation(Long userId, String action, String target, Instant expiresAt) { }
    public record Issued(String token, long expiresInSeconds) { }
}

package cn.iocoder.yudao.curmerce.agent;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** One-time user-bound confirmation tokens for future sensitive Agent tools. */
@Service
public class AgentConfirmationService {
    private static final Duration TTL = Duration.ofMinutes(5);
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Confirmation> tokens = new ConcurrentHashMap<>();
    private final AgentCoreClient coreClient;

    public AgentConfirmationService(AgentCoreClient coreClient) { this.coreClient = coreClient; }

    public Issued issue(String authorization, String action, String target) {
        Long userId = coreClient.authenticate(authorization);
        if (action == null || action.isBlank() || target == null || target.isBlank())
            throw new IllegalArgumentException("确认动作和目标不能为空");
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random.generateSeed(24));
        tokens.put(token, new Confirmation(userId, action.trim(), target.trim(), Instant.now().plus(TTL)));
        return new Issued(token, TTL.toSeconds());
    }

    public void consume(String authorization, String token, String action, String target) {
        Long userId = coreClient.authenticate(authorization);
        Confirmation confirmation = tokens.get(token);
        if (confirmation == null || confirmation.expiresAt().isBefore(Instant.now())
                || !confirmation.userId().equals(userId)
                || !confirmation.action().equals(action) || !confirmation.target().equals(target)) {
            throw new AgentAuthorizationException("确认令牌无效、过期或不匹配");
        }
        // Consume only after all bindings match; an unrelated caller must not be
        // able to invalidate a valid token belonging to another user.
        if (!tokens.remove(token, confirmation)) {
            throw new AgentAuthorizationException("确认令牌已被消费");
        }
    }

    public static class AgentAuthorizationException extends RuntimeException {
        public AgentAuthorizationException(String message) { super(message); }
    }
    private record Confirmation(Long userId, String action, String target, Instant expiresAt) { }
    public record Issued(String token, long expiresInSeconds) { }
}

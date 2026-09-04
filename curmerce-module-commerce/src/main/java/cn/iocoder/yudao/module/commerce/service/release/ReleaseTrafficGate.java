package cn.iocoder.yudao.module.commerce.service.release;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bounded entrance protection for flash-sale traffic. It deliberately runs
 * before the inventory reservation so bursts are shed before MySQL locks are
 * acquired. The inventory gate remains the source of truth for correctness.
 */
@Component
public class ReleaseTrafficGate {
    private static final String PREFIX = "curmerce:release:traffic:v1:";
    private static final DefaultRedisScript<Long> WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
            if current > tonumber(ARGV[1]) then return 0 end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final int campaignLimit;
    private final int userLimit;
    private final int windowSeconds;

    public ReleaseTrafficGate(StringRedisTemplate redis,
                              @Value("${curmerce.release.traffic-gate-enabled:true}") boolean enabled,
                              @Value("${curmerce.release.traffic-campaign-requests-per-window:2000}") int campaignLimit,
                              @Value("${curmerce.release.traffic-user-requests-per-window:20}") int userLimit,
                              @Value("${curmerce.release.traffic-window-seconds:1}") int windowSeconds) {
        this.redis = redis;
        this.enabled = enabled;
        this.campaignLimit = Math.max(1, campaignLimit);
        this.userLimit = Math.max(1, userLimit);
        this.windowSeconds = Math.max(1, windowSeconds);
    }

    public Result allow(Long campaignId, Long userId) {
        if (!enabled) return Result.DISABLED;
        try {
            long bucket = System.currentTimeMillis() / (windowSeconds * 1000L);
            Long campaign = redis.execute(WINDOW_SCRIPT,
                    List.of(PREFIX + "campaign:" + campaignId + ":" + bucket),
                    String.valueOf(campaignLimit), String.valueOf(windowSeconds));
            if (!Long.valueOf(1L).equals(campaign)) return Result.LIMITED;
            Long user = redis.execute(WINDOW_SCRIPT,
                    List.of(PREFIX + "user:" + campaignId + ":" + userId + ":" + bucket),
                    String.valueOf(userLimit), String.valueOf(windowSeconds));
            return Long.valueOf(1L).equals(user) ? Result.ALLOWED : Result.LIMITED;
        } catch (RuntimeException ex) {
            return Result.UNAVAILABLE;
        }
    }

    public enum Result { ALLOWED, LIMITED, UNAVAILABLE, DISABLED }
}

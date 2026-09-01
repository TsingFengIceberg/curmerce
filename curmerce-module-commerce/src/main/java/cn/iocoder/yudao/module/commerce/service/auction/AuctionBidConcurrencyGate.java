package cn.iocoder.yudao.module.commerce.service.auction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/** Optional Redis/Lua fast path for the hot leading-price check. */
@Component
public class AuctionBidConcurrencyGate {
    private static final String PREFIX = "curmerce:auction:bid:v1:";
    private static final DefaultRedisScript<Long> ACCEPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[3]) = 1 then return 2 end
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            local minimum = tonumber(ARGV[1])
            local amount = tonumber(ARGV[2])
            if amount < minimum or amount <= current then return -1 end
            redis.call('SET', KEYS[1], amount, 'EX', ARGV[4])
            redis.call('SET', KEYS[2], ARGV[3], 'EX', ARGV[4])
            redis.call('SET', KEYS[3], amount, 'EX', ARGV[4])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RECONCILE = new DefaultRedisScript<>("""
            if tonumber(ARGV[1]) < 0 then
              redis.call('DEL', KEYS[1], KEYS[2])
            else
              redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
              redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final long ttlSeconds;

    public AuctionBidConcurrencyGate(StringRedisTemplate redis,
                                     @Value("${curmerce.auction.redis-gate-enabled:false}") boolean enabled,
                                     @Value("${curmerce.auction.redis-gate-ttl-seconds:86400}") long ttlSeconds) {
        this.redis = redis;
        this.enabled = enabled;
        this.ttlSeconds = Math.max(60, Math.min(ttlSeconds, 7 * 24 * 60 * 60));
    }

    public boolean enabled() { return enabled; }

    public Result tryAccept(Long sessionId, Long amount, Long minimum, Long bidderUserId, String idempotencyKey) {
        if (!enabled) return Result.DISABLED;
        try {
            Long result = redis.execute(ACCEPT, List.of(amountKey(sessionId), bidderKey(sessionId), requestKey(sessionId, idempotencyKey)),
                    String.valueOf(minimum), String.valueOf(amount), String.valueOf(bidderUserId), String.valueOf(ttlSeconds));
            if (result == null) return Result.UNAVAILABLE;
            if (result == 2L) return Result.DUPLICATE;
            if (result < 0L) return Result.BELOW_MINIMUM;
            return Result.ACCEPTED;
        } catch (RuntimeException ex) {
            return Result.UNAVAILABLE;
        }
    }

    public boolean reconcile(Long sessionId, Long highestAmount, Long bidderUserId) {
        if (!enabled) return true;
        try {
            Long result = redis.execute(RECONCILE, List.of(amountKey(sessionId), bidderKey(sessionId)),
                    String.valueOf(highestAmount == null ? -1 : highestAmount),
                    String.valueOf(bidderUserId == null ? "" : bidderUserId), String.valueOf(ttlSeconds));
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String amountKey(Long id) { return PREFIX + "amount:" + id; }
    private static String bidderKey(Long id) { return PREFIX + "bidder:" + id; }
    private static String requestKey(Long id, String key) { return PREFIX + "request:" + id + ":" + key; }

    public enum Result { ACCEPTED, BELOW_MINIMUM, DUPLICATE, UNAVAILABLE, DISABLED }
}

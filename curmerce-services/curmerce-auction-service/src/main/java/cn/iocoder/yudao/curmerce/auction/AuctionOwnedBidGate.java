package cn.iocoder.yudao.curmerce.auction;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/** Redis/Lua leading-price gate for the Auction-owned write model. */
@Component
public class AuctionOwnedBidGate {
    private static final String PREFIX = "curmerce:auction-owned:bid:v1:";
    private static final DefaultRedisScript<Long> ACCEPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[3]) = 1 then return 2 end
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')
            if tonumber(ARGV[2]) < tonumber(ARGV[1]) or tonumber(ARGV[2]) <= current then return -1 end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[4])
            redis.call('SET', KEYS[2], ARGV[3], 'EX', ARGV[4])
            redis.call('SET', KEYS[3], ARGV[2], 'EX', ARGV[4])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RECONCILE = new DefaultRedisScript<>("""
            if tonumber(ARGV[1]) < 0 then redis.call('DEL', KEYS[1], KEYS[2])
            else redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3]); redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3]) end
            return 1
            """, Long.class);
    private final StringRedisTemplate redis;
    private final AuctionServiceProperties properties;

    public AuctionOwnedBidGate(StringRedisTemplate redis, AuctionServiceProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    public Result tryAccept(Long sessionId, Long amount, Long minimum, Long userId, String key) {
        if (!properties.redisGateEnabled()) return Result.DISABLED;
        try {
            Long result = redis.execute(ACCEPT, List.of(key(sessionId, "amount"), key(sessionId, "user"), key(sessionId, "request") + ":" + key),
                    String.valueOf(minimum), String.valueOf(amount), String.valueOf(userId), String.valueOf(properties.redisGateTtlSeconds()));
            if (result == null) return Result.UNAVAILABLE;
            if (result == 2L) return Result.DUPLICATE;
            return result < 0 ? Result.BELOW_MINIMUM : Result.ACCEPTED;
        } catch (RuntimeException ex) { return Result.UNAVAILABLE; }
    }

    public boolean reconcile(Long sessionId, Long amount, Long userId) {
        if (!properties.redisGateEnabled()) return true;
        try {
            Long result = redis.execute(RECONCILE, List.of(key(sessionId, "amount"), key(sessionId, "user")),
                    String.valueOf(amount == null ? -1 : amount), String.valueOf(userId == null ? "" : userId), String.valueOf(properties.redisGateTtlSeconds()));
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException ex) { return false; }
    }

    private static String key(Long sessionId, String part) { return PREFIX + part + ":" + sessionId; }
    public enum Result { ACCEPTED, BELOW_MINIMUM, DUPLICATE, UNAVAILABLE, DISABLED }
}

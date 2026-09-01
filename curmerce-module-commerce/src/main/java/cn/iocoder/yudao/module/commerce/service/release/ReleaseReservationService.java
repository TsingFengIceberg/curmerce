package cn.iocoder.yudao.module.commerce.service.release;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis/Lua inventory gate for limited releases. MySQL remains the source of
 * truth; this gate reduces database lock contention and never replaces the
 * transactional database decrement.
 */
@Component
public class ReleaseReservationService {
    private static final String KEY_PREFIX = "curmerce:release:v1:";
    private static final long USER_KEY_TTL_SECONDS = 7 * 24 * 60 * 60L;
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
            if stock < 0 then return -3 end
            local quantity = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local user = tonumber(redis.call('GET', KEYS[3]) or '0')
            if user + quantity > limit then return -2 end
            if stock < quantity then return -1 end
            redis.call('DECRBY', KEYS[1], quantity)
            redis.call('INCRBY', KEYS[2], quantity)
            redis.call('INCRBY', KEYS[3], quantity)
            redis.call('EXPIRE', KEYS[3], ARGV[3])
            redis.call('EXPIRE', KEYS[2], ARGV[3])
            return stock - quantity
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            local reserved = tonumber(redis.call('GET', KEYS[2]) or '0')
            local quantity = tonumber(ARGV[1])
            if reserved < quantity then return 0 end
            redis.call('INCRBY', KEYS[1], quantity)
            redis.call('DECRBY', KEYS[2], quantity)
            local user = tonumber(redis.call('GET', KEYS[3]) or '0')
            if user <= quantity then redis.call('DEL', KEYS[3])
            else redis.call('DECRBY', KEYS[3], quantity) end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> COMMIT_SCRIPT = new DefaultRedisScript<>("""
            local reserved = tonumber(redis.call('GET', KEYS[2]) or '0')
            local quantity = tonumber(ARGV[1])
            if reserved < quantity then return 0 end
            redis.call('DECRBY', KEYS[2], quantity)
            if reserved == quantity then redis.call('DEL', KEYS[2]) end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final boolean enabled;

    public ReleaseReservationService(StringRedisTemplate redis,
                                      @Value("${curmerce.release.redis-reservation-enabled:true}") boolean enabled) {
        this.redis = redis;
        this.enabled = enabled;
    }

    public ReservationResult reserve(Long campaignId, Long itemId, Long userId,
                                     int quantity, int userLimit, int databaseStock) {
        if (!enabled) {
            return ReservationResult.DISABLED;
        }
        String stockKey = stockKey(campaignId, itemId);
        String reservedKey = reservedKey(campaignId, itemId);
        String userKey = userKey(campaignId, itemId, userId);
        try {
            redis.opsForValue().setIfAbsent(stockKey, String.valueOf(databaseStock));
            redis.opsForValue().setIfAbsent(reservedKey, "0");
            Long result = redis.execute(RESERVE_SCRIPT, List.of(stockKey, reservedKey, userKey),
                    String.valueOf(quantity), String.valueOf(userLimit), String.valueOf(USER_KEY_TTL_SECONDS));
            if (result == null || result == -3L) return ReservationResult.UNAVAILABLE;
            if (result == -2L) return ReservationResult.LIMIT_EXCEEDED;
            if (result == -1L) return ReservationResult.STOCK_INSUFFICIENT;
            return ReservationResult.RESERVED;
        } catch (RuntimeException ex) {
            return ReservationResult.UNAVAILABLE;
        }
    }

    public boolean release(Long campaignId, Long itemId, Long userId, int quantity) {
        if (!enabled) return true;
        try {
            Long result = redis.execute(RELEASE_SCRIPT,
                    List.of(stockKey(campaignId, itemId), reservedKey(campaignId, itemId),
                            userKey(campaignId, itemId, userId)), String.valueOf(quantity));
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Confirm a committed database purchase while retaining the user's limit counter. */
    public boolean commit(Long campaignId, Long itemId, int quantity) {
        if (!enabled) return true;
        try {
            Long result = redis.execute(COMMIT_SCRIPT,
                    List.of(stockKey(campaignId, itemId), reservedKey(campaignId, itemId)), String.valueOf(quantity));
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Reconcile only when no in-flight Redis reservation exists. */
    public boolean reconcileStock(Long campaignId, Long itemId, int databaseStock) {
        if (!enabled) return true;
        try {
            String reservedKey = reservedKey(campaignId, itemId);
            Long reserved = parse(redis.opsForValue().get(reservedKey));
            if (reserved != null && reserved > 0) return false;
            redis.opsForValue().set(stockKey(campaignId, itemId), String.valueOf(databaseStock));
            redis.opsForValue().setIfAbsent(reservedKey, "0");
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String stockKey(Long campaignId, Long itemId) {
        return KEY_PREFIX + "stock:" + campaignId + ":" + itemId;
    }

    private String reservedKey(Long campaignId, Long itemId) {
        return KEY_PREFIX + "reserved:" + campaignId + ":" + itemId;
    }

    private String userKey(Long campaignId, Long itemId, Long userId) {
        return KEY_PREFIX + "user:" + campaignId + ":" + itemId + ":" + userId;
    }

    private Long parse(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    public enum ReservationResult {
        RESERVED, STOCK_INSUFFICIENT, LIMIT_EXCEEDED, UNAVAILABLE, DISABLED
    }
}

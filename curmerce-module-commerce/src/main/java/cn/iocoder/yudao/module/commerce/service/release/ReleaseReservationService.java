package cn.iocoder.yudao.module.commerce.service.release;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Redis/Lua inventory gate for limited releases. MySQL remains the source of
 * truth; this gate reduces database lock contention and never replaces the
 * transactional database decrement.
 */
@Component
public class ReleaseReservationService {
    private static final String KEY_PREFIX = "curmerce:release:v1:";
    private static final String ACTIVE_RESERVATIONS_KEY = KEY_PREFIX + "active-reservations";
    private static final String TENANT_REGISTRY_KEY = KEY_PREFIX + "tenants";
    private static final String DEFAULT_TENANT_SCOPE = "default";
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
    private static final DefaultRedisScript<Long> RESTORE_COMMITTED_PURCHASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[3]) == 1 then return 1 end
            local quantity = tonumber(ARGV[1])
            local databaseStock = tonumber(ARGV[2])
            if redis.call('EXISTS', KEYS[1]) == 0 then
                redis.call('SET', KEYS[1], databaseStock)
            else
                redis.call('INCRBY', KEYS[1], quantity)
            end
            local user = tonumber(redis.call('GET', KEYS[2]) or '0')
            if user <= quantity then redis.call('DEL', KEYS[2])
            else redis.call('DECRBY', KEYS[2], quantity) end
            redis.call('SET', KEYS[3], '1', 'EX', ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE_IDEMPOTENT_SCRIPT = new DefaultRedisScript<>("""
            local reserved = tonumber(redis.call('GET', KEYS[2]) or '0')
            local quantity = tonumber(ARGV[1])
            local requested = tonumber(redis.call('GET', KEYS[4]) or '-1')
            if requested ~= quantity then return 0 end
            if reserved < quantity then return 0 end
            redis.call('INCRBY', KEYS[1], quantity)
            redis.call('DECRBY', KEYS[2], quantity)
            local user = tonumber(redis.call('GET', KEYS[3]) or '0')
            if user <= quantity then redis.call('DEL', KEYS[3])
            else redis.call('DECRBY', KEYS[3], quantity) end
            redis.call('DEL', KEYS[4])
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
    private static final DefaultRedisScript<Long> RECONCILE_SCRIPT = new DefaultRedisScript<>("""
            local reserved = tonumber(redis.call('GET', KEYS[2]) or '0')
            if reserved > 0 then return 0 end
            redis.call('SET', KEYS[1], ARGV[1])
            if redis.call('EXISTS', KEYS[2]) == 0 then redis.call('SET', KEYS[2], '0') end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RESERVE_IDEMPOTENT_SCRIPT = new DefaultRedisScript<>("""
            local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
            if stock < 0 then return -3 end
            local quantity = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local previous = tonumber(redis.call('GET', KEYS[4]) or '-1')
            if previous >= 0 then
                if previous ~= quantity then return -5 end
                return -4
            end
            local user = tonumber(redis.call('GET', KEYS[3]) or '0')
            if user + quantity > limit then return -2 end
            if stock < quantity then return -1 end
            redis.call('DECRBY', KEYS[1], quantity)
            redis.call('INCRBY', KEYS[2], quantity)
            redis.call('INCRBY', KEYS[3], quantity)
            redis.call('SET', KEYS[4], quantity, 'EX', ARGV[3])
            redis.call('EXPIRE', KEYS[3], ARGV[3])
            redis.call('EXPIRE', KEYS[2], ARGV[3])
            return stock - quantity
            """, Long.class);

    /** Same reservation gate with a durable active-reservation index. */
    private static final DefaultRedisScript<Long> RESERVE_TRACKED_SCRIPT = new DefaultRedisScript<>("""
            local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
            if stock < 0 then return -3 end
            local quantity = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local previous = tonumber(redis.call('GET', KEYS[4]) or '-1')
            if previous >= 0 then
                if previous ~= quantity then return -5 end
                return -4
            end
            local user = tonumber(redis.call('GET', KEYS[3]) or '0')
            if user + quantity > limit then return -2 end
            if stock < quantity then return -1 end
            redis.call('DECRBY', KEYS[1], quantity)
            redis.call('INCRBY', KEYS[2], quantity)
            redis.call('INCRBY', KEYS[3], quantity)
            redis.call('SET', KEYS[4], quantity, 'EX', ARGV[3])
            redis.call('EXPIRE', KEYS[3], ARGV[3])
            redis.call('EXPIRE', KEYS[2], ARGV[3])
            redis.call('SADD', KEYS[5], KEYS[6])
            redis.call('SADD', KEYS[7], ARGV[9])
            redis.call('HSET', KEYS[6], 'tenantId', ARGV[9], 'campaignId', ARGV[4], 'itemId', ARGV[5],
                    'userId', ARGV[6], 'quantity', ARGV[1], 'reservationKey', ARGV[7],
                    'createdAt', ARGV[8])
            redis.call('EXPIRE', KEYS[6], ARGV[3])
            return stock - quantity
            """, Long.class);

    /** Finalizes a tracked reservation after the SQL purchase has committed. */
    private static final DefaultRedisScript<Long> COMMIT_TRACKED_SCRIPT = new DefaultRedisScript<>("""
            local request = redis.call('GET', KEYS[3])
            if not request then
                if redis.call('SISMEMBER', KEYS[4], KEYS[5]) == 0 then return 1 end
                return 0
            end
            local reserved = tonumber(redis.call('GET', KEYS[2]) or '0')
            local quantity = tonumber(ARGV[1])
            if reserved < quantity then return 0 end
            redis.call('DECRBY', KEYS[2], quantity)
            if reserved == quantity then redis.call('DEL', KEYS[2]) end
            redis.call('DEL', KEYS[3])
            redis.call('SREM', KEYS[4], KEYS[5])
            redis.call('DEL', KEYS[5])
            return 1
            """, Long.class);

    /**
     * Repairs a committed ledger after the short-lived Redis request/hash
     * marker has expired or Redis has restarted. Resetting the Redis stock to
     * the MySQL value is safe only when no other reservation remains.
     */
    private static final DefaultRedisScript<Long> RECOVER_COMMITTED_SCRIPT = new DefaultRedisScript<>("""
            local request = redis.call('EXISTS', KEYS[3])
            local reservation = redis.call('EXISTS', KEYS[5])
            local reserved = tonumber(redis.call('GET', KEYS[2]) or '0')
            local quantity = tonumber(ARGV[1])
            -- A committed ledger row is authoritative for this reservation.
            -- If only the short-lived request marker expired, the reservation
            -- hash still identifies the same hold and can be finalized even
            -- when other users have reservations on the item.  The previous
            -- implementation refused this case forever because it treated
            -- any non-zero aggregate reservation as an unrelated hold.
            if request == 1 or reservation == 1 then
                if reserved < quantity then return 0 end
                redis.call('DECRBY', KEYS[2], quantity)
                if reserved == quantity then redis.call('DEL', KEYS[2]) end
                redis.call('DEL', KEYS[3], KEYS[5])
                redis.call('SREM', KEYS[4], KEYS[5])
                return 1
            end
            -- With no reservation metadata, resetting the hot stock is safe
            -- only when no other active hold exists. The database value is
            -- the source of truth for this recovery path.
            if reserved > 0 then return 0 end
            redis.call('SET', KEYS[1], ARGV[2])
            redis.call('SREM', KEYS[4], KEYS[5])
            return 1
            """, Long.class);

    /** Releases a tracked reservation and removes its recovery metadata atomically. */
    private static final DefaultRedisScript<Long> RELEASE_TRACKED_SCRIPT = new DefaultRedisScript<>("""
            local requested = tonumber(redis.call('GET', KEYS[3]) or '-1')
            local quantity = tonumber(ARGV[1])
            if requested ~= quantity then
                if redis.call('SISMEMBER', KEYS[4], KEYS[5]) == 0 then return 1 end
                return 0
            end
            local reserved = tonumber(redis.call('GET', KEYS[2]) or '0')
            if reserved < quantity then return 0 end
            redis.call('INCRBY', KEYS[1], quantity)
            redis.call('DECRBY', KEYS[2], quantity)
            local user = tonumber(redis.call('GET', KEYS[6]) or '0')
            if user <= quantity then redis.call('DEL', KEYS[6])
            else redis.call('DECRBY', KEYS[6], quantity) end
            redis.call('DEL', KEYS[3])
            redis.call('SREM', KEYS[4], KEYS[5])
            redis.call('DEL', KEYS[5])
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
        return reserve(campaignId, itemId, userId, quantity, userLimit, databaseStock, null);
    }

    /** Atomic reservation with a request key, preventing duplicate HTTP retries from consuming stock twice. */
    public ReservationResult reserve(Long campaignId, Long itemId, Long userId,
                                     int quantity, int userLimit, int databaseStock, String idempotencyKey) {
        if (!enabled) {
            return ReservationResult.DISABLED;
        }
        validate(campaignId, itemId, userId, quantity, userLimit, databaseStock, idempotencyKey);
        String tenant = tenantScope();
        String stockKey = stockKey(tenant, campaignId, itemId);
        String reservedKey = reservedKey(tenant, campaignId, itemId);
        String userKey = userKey(tenant, campaignId, itemId, userId);
        try {
            redis.opsForValue().setIfAbsent(stockKey, String.valueOf(databaseStock));
            redis.opsForValue().setIfAbsent(reservedKey, "0");
            boolean keyed = idempotencyKey != null && !idempotencyKey.isBlank();
            List<String> keys = keyed ? List.of(stockKey, reservedKey, userKey,
                    requestKey(tenant, campaignId, itemId, userId, idempotencyKey))
                    : List.of(stockKey, reservedKey, userKey);
            Long result = keyed
                    ? redis.execute(RESERVE_IDEMPOTENT_SCRIPT, keys, String.valueOf(quantity), String.valueOf(userLimit), String.valueOf(USER_KEY_TTL_SECONDS))
                    : redis.execute(RESERVE_SCRIPT, keys, String.valueOf(quantity), String.valueOf(userLimit), String.valueOf(USER_KEY_TTL_SECONDS));
            if (result == null || result == -3L) return ReservationResult.UNAVAILABLE;
            if (result == -2L) return ReservationResult.LIMIT_EXCEEDED;
            if (result == -1L) return ReservationResult.STOCK_INSUFFICIENT;
            if (result == -4L) return ReservationResult.ALREADY_RESERVED;
            if (result == -5L) return ReservationResult.IDEMPOTENCY_CONFLICT;
            return ReservationResult.RESERVED;
        } catch (RuntimeException ex) {
            return ReservationResult.UNAVAILABLE;
        }
    }

    /**
     * Atomic reservation variant that also records a bounded recovery index.
     * Kept separate from the compatibility method above so existing callers
     * and unit-test doubles retain the original Redis command shape.
     */
    public ReservationResult reserveTracked(Long campaignId, Long itemId, Long userId,
                                            int quantity, int userLimit, int databaseStock,
                                            String reservationKey) {
        if (!enabled) return ReservationResult.DISABLED;
        validate(campaignId, itemId, userId, quantity, userLimit, databaseStock, reservationKey);
        if (reservationKey == null || reservationKey.isBlank()) throw new IllegalArgumentException("预占幂等键不能为空");
        String tenant = tenantScope();
        String stockKey = stockKey(tenant, campaignId, itemId);
        String reservedKey = reservedKey(tenant, campaignId, itemId);
        String userKey = userKey(tenant, campaignId, itemId, userId);
        try {
            redis.opsForValue().setIfAbsent(stockKey, String.valueOf(databaseStock));
            redis.opsForValue().setIfAbsent(reservedKey, "0");
            Long result = redis.execute(RESERVE_TRACKED_SCRIPT,
                    List.of(stockKey, reservedKey, userKey, requestKey(tenant, campaignId, itemId, userId, reservationKey),
                            activeReservationsKey(tenant), reservationHashKey(tenant, campaignId, itemId, userId, reservationKey), TENANT_REGISTRY_KEY),
                    String.valueOf(quantity), String.valueOf(userLimit), String.valueOf(USER_KEY_TTL_SECONDS),
                    String.valueOf(campaignId), String.valueOf(itemId), String.valueOf(userId), reservationKey,
                    String.valueOf(System.currentTimeMillis()), tenant);
            if (result == null || result == -3L) return ReservationResult.UNAVAILABLE;
            if (result == -2L) return ReservationResult.LIMIT_EXCEEDED;
            if (result == -1L) return ReservationResult.STOCK_INSUFFICIENT;
            if (result == -4L) return ReservationResult.ALREADY_RESERVED;
            if (result == -5L) return ReservationResult.IDEMPOTENCY_CONFLICT;
            return ReservationResult.RESERVED;
        } catch (RuntimeException ex) { return ReservationResult.UNAVAILABLE; }
    }

    public boolean release(Long campaignId, Long itemId, Long userId, int quantity) {
        return release(campaignId, itemId, userId, quantity, null);
    }

    public boolean release(Long campaignId, Long itemId, Long userId, int quantity, String idempotencyKey) {
        if (!enabled) return true;
        validateIdentity(campaignId, itemId, userId, quantity);
        try {
            boolean keyed = idempotencyKey != null && !idempotencyKey.isBlank();
            List<String> keys = keyed
                    ? List.of(stockKey(campaignId, itemId), reservedKey(campaignId, itemId),
                    userKey(campaignId, itemId, userId), requestKey(campaignId, itemId, userId, idempotencyKey))
                    : List.of(stockKey(campaignId, itemId), reservedKey(campaignId, itemId),
                    userKey(campaignId, itemId, userId));
            Long result = keyed
                    ? redis.execute(RELEASE_IDEMPOTENT_SCRIPT, keys, String.valueOf(quantity))
                    : redis.execute(RELEASE_SCRIPT, keys, String.valueOf(quantity));
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Returns a stable key for the durable reservation ledger. Blank request
     * keys receive a generated value so every reservation can be recovered;
     * explicit request keys remain stable across HTTP/Kafka retries.
     */
    public String reservationKey(String idempotencyKey) {
        String value = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (value.isBlank()) return UUID.randomUUID().toString();
        if (value.length() > 128) throw new IllegalArgumentException("幂等键过长");
        return value;
    }

    /** Tracked release used by the SQL transaction completion callback. */
    public boolean releaseTracked(Long campaignId, Long itemId, Long userId, int quantity, String reservationKey) {
        return releaseTracked(tenantScope(), campaignId, itemId, userId, quantity, reservationKey);
    }

    /** Explicit-tenant variant used by background recovery workers. */
    public boolean releaseTracked(String tenant, Long campaignId, Long itemId, Long userId,
                                  int quantity, String reservationKey) {
        if (!enabled) return true;
        validateIdentity(campaignId, itemId, userId, quantity);
        if (reservationKey == null || reservationKey.isBlank()) return false;
        try {
            Long result = redis.execute(RELEASE_TRACKED_SCRIPT,
                    List.of(stockKey(tenant, campaignId, itemId), reservedKey(tenant, campaignId, itemId),
                            requestKey(tenant, campaignId, itemId, userId, reservationKey), activeReservationsKey(tenant),
                            reservationHashKey(tenant, campaignId, itemId, userId, reservationKey),
                            userKey(tenant, campaignId, itemId, userId)), String.valueOf(quantity));
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException ex) { return false; }
    }

    /** Confirm a committed database purchase while retaining the user's limit counter. */
    public boolean commit(Long campaignId, Long itemId, int quantity) {
        if (!enabled) return true;
        if (campaignId == null || itemId == null || quantity <= 0) return false;
        try {
            Long result = redis.execute(COMMIT_SCRIPT,
                    List.of(stockKey(campaignId, itemId), reservedKey(campaignId, itemId)), String.valueOf(quantity));
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Tracked finalization used after the surrounding SQL transaction commits. */
    public boolean commitTracked(Long campaignId, Long itemId, Long userId, int quantity, String reservationKey) {
        return commitTracked(tenantScope(), campaignId, itemId, userId, quantity, reservationKey);
    }

    /** Explicit-tenant variant for scheduled workers that do not have a web context. */
    public boolean commitTracked(String tenant, Long campaignId, Long itemId, Long userId,
                                 int quantity, String reservationKey) {
        if (!enabled) return true;
        if (campaignId == null || itemId == null || userId == null || quantity <= 0
                || reservationKey == null || reservationKey.isBlank()) return false;
        try {
            Long result = redis.execute(COMMIT_TRACKED_SCRIPT,
                    List.of(stockKey(tenant, campaignId, itemId), reservedKey(tenant, campaignId, itemId),
                            requestKey(tenant, campaignId, itemId, userId, reservationKey), activeReservationsKey(tenant),
                            reservationHashKey(tenant, campaignId, itemId, userId, reservationKey)), String.valueOf(quantity));
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException ex) { return false; }
    }

    /**
     * Completes a committed reservation when its Redis request marker expired.
     * The script finalizes this reservation when its identity hash remains,
     * and refuses to reset the aggregate stock while unrelated holds remain.
     */
    public boolean recoverCommitted(String tenant, Long campaignId, Long itemId, Long userId,
                                    int quantity, String reservationKey, int databaseStock) {
        if (!enabled) return true;
        if (tenant == null || tenant.isBlank() || campaignId == null || itemId == null || userId == null
                || quantity <= 0 || reservationKey == null || reservationKey.isBlank() || databaseStock < 0) {
            return false;
        }
        try {
            Long result = redis.execute(RECOVER_COMMITTED_SCRIPT,
                    List.of(stockKey(tenant, campaignId, itemId), reservedKey(tenant, campaignId, itemId),
                            requestKey(tenant, campaignId, itemId, userId, reservationKey), activeReservationsKey(tenant),
                            reservationHashKey(tenant, campaignId, itemId, userId, reservationKey)),
                    String.valueOf(quantity), String.valueOf(databaseStock));
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException ex) { return false; }
    }

    /**
     * Restores one already committed purchase after its payment is cancelled.
     * The purchase id is the idempotency fence: a database transaction may be
     * retried after Redis has completed but before the SQL commit is visible.
     */
    public boolean restoreCommittedPurchase(Long campaignId, Long itemId, Long userId,
                                            Long purchaseId, int quantity, int databaseStock) {
        if (!enabled) return true;
        validateIdentity(campaignId, itemId, userId, quantity);
        if (purchaseId == null || purchaseId <= 0 || databaseStock < 0) {
            throw new IllegalArgumentException("限时发售已提交购买恢复参数无效");
        }
        try {
            Long result = redis.execute(RESTORE_COMMITTED_PURCHASE_SCRIPT,
                    List.of(stockKey(campaignId, itemId), userKey(campaignId, itemId, userId),
                            restoreMarkerKey(campaignId, itemId, purchaseId)),
                    String.valueOf(quantity), String.valueOf(databaseStock), String.valueOf(USER_KEY_TTL_SECONDS));
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Reconcile only when no in-flight Redis reservation exists. */
    public boolean reconcileStock(Long campaignId, Long itemId, int databaseStock) {
        return reconcileStock(tenantScope(), campaignId, itemId, databaseStock);
    }

    /** Explicit-tenant stock reconciliation used by multi-tenant workers. */
    public boolean reconcileStock(String tenant, Long campaignId, Long itemId, int databaseStock) {
        if (!enabled) return true;
        try {
            Long result = redis.execute(RECONCILE_SCRIPT,
                    List.of(stockKey(tenant, campaignId, itemId), reservedKey(tenant, campaignId, itemId)),
                    String.valueOf(Math.max(0, databaseStock)));
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Lists bounded Redis reservation metadata for crash recovery. The active
     * set is only an index; each hash contains the identity needed to ask the
     * SQL ledger whether the reservation was committed.
     */
    public List<ActiveReservation> activeReservations(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        Set<String> scopes = tenantScopes();
        if (scopes.isEmpty()) scopes = Set.of(tenantScope());
        List<ActiveReservation> result = new ArrayList<>();
        for (String scope : scopes) {
            if (result.size() >= safeLimit) break;
            result.addAll(activeReservations(scope, safeLimit - result.size()));
        }
        return List.copyOf(result);
    }

    /** Lists reservations for an explicit tenant, suitable for scheduled recovery. */
    public List<ActiveReservation> activeReservations(String tenant, int limit) {
        if (!enabled) return List.of();
        if (tenant == null || tenant.isBlank()) return List.of();
        int safeLimit = Math.max(1, Math.min(limit, 500));
        try {
            Set<String> keys = redis.opsForSet().members(activeReservationsKey(tenant));
            if (keys == null || keys.isEmpty()) return List.of();
            List<ActiveReservation> result = new ArrayList<>();
            for (String key : keys) {
                if (result.size() >= safeLimit) break;
                Map<Object, Object> values = redis.opsForHash().entries(key);
                // The reservation hash has its own TTL while the set is a
                // recovery index. Redis does not expire set members, so an
                // expired hash must be removed here or the index grows
                // forever and repeatedly looks like an in-flight lease.
                if (values == null || values.isEmpty()) {
                    redis.opsForSet().remove(activeReservationsKey(tenant), key);
                    continue;
                }
                Long campaign = parseLong(values.get("campaignId"));
                Long item = parseLong(values.get("itemId"));
                Long user = parseLong(values.get("userId"));
                Integer quantity = parseIntValue(values.get("quantity"));
                Long created = parseLong(values.get("createdAt"));
                String reservation = stringValue(values.get("reservationKey"));
                if (campaign != null && item != null && user != null && quantity != null
                        && quantity > 0 && created != null && reservation != null) {
                    String reservationTenant = stringValue(values.get("tenantId"));
                    if (reservationTenant == null || reservationTenant.isBlank()) reservationTenant = tenant;
                    result.add(new ActiveReservation(key, reservationTenant, campaign, item, user, quantity,
                            reservation, created));
                } else {
                    // A malformed member cannot be safely released because
                    // its identity is incomplete. Remove only its index
                    // entry; the per-reservation hash remains recoverable if
                    // an operator needs to inspect it later.
                    redis.opsForSet().remove(activeReservationsKey(tenant), key);
                }
            }
            return List.copyOf(result);
        } catch (RuntimeException ex) { return List.of(); }
    }

    /** Tenant scopes observed by the tracked reservation gate. */
    public Set<String> tenantScopes() {
        if (!enabled) return Set.of();
        try {
            Set<String> scopes = redis.opsForSet().members(TENANT_REGISTRY_KEY);
            return scopes == null ? Set.of() : Set.copyOf(scopes);
        } catch (RuntimeException ex) {
            return Set.of();
        }
    }

    private String stockKey(Long campaignId, Long itemId) {
        return stockKey(tenantScope(), campaignId, itemId);
    }

    private String stockKey(String tenant, Long campaignId, Long itemId) {
        return KEY_PREFIX + tenant + ":stock:" + campaignId + ":" + itemId;
    }

    private String reservedKey(Long campaignId, Long itemId) {
        return reservedKey(tenantScope(), campaignId, itemId);
    }

    private String reservedKey(String tenant, Long campaignId, Long itemId) {
        return KEY_PREFIX + tenant + ":reserved:" + campaignId + ":" + itemId;
    }

    private String userKey(Long campaignId, Long itemId, Long userId) {
        return userKey(tenantScope(), campaignId, itemId, userId);
    }

    private String userKey(String tenant, Long campaignId, Long itemId, Long userId) {
        return KEY_PREFIX + tenant + ":user:" + campaignId + ":" + itemId + ":" + userId;
    }

    private String requestKey(Long campaignId, Long itemId, Long userId, String idempotencyKey) {
        return requestKey(tenantScope(), campaignId, itemId, userId, idempotencyKey);
    }

    private String requestKey(String tenant, Long campaignId, Long itemId, Long userId, String idempotencyKey) {
        return KEY_PREFIX + tenant + ":request:" + campaignId + ":" + itemId + ":" + userId + ":" + digest(idempotencyKey.trim());
    }

    private String reservationHashKey(Long campaignId, Long itemId, Long userId, String reservationKey) {
        return reservationHashKey(tenantScope(), campaignId, itemId, userId, reservationKey);
    }

    private String reservationHashKey(String tenant, Long campaignId, Long itemId, Long userId, String reservationKey) {
        return KEY_PREFIX + tenant + ":reservation:" + campaignId + ":" + itemId + ":" + userId + ":" + digest(reservationKey);
    }

    private String restoreMarkerKey(Long campaignId, Long itemId, Long purchaseId) {
        return KEY_PREFIX + tenantScope() + ":restore:" + campaignId + ":" + itemId + ":" + purchaseId;
    }

    private String tenantScope() {
        Long tenant = TenantContextHolder.getTenantId();
        return tenant == null ? DEFAULT_TENANT_SCOPE : String.valueOf(tenant);
    }

    private String activeReservationsKey(String tenant) {
        return ACTIVE_RESERVATIONS_KEY + ":" + tenant;
    }

    private static void validate(Long campaignId, Long itemId, Long userId, int quantity,
                                 int userLimit, int databaseStock, String idempotencyKey) {
        validateIdentity(campaignId, itemId, userId, quantity);
        if (userLimit <= 0 || databaseStock < 0) throw new IllegalArgumentException("限时发售库存或限购参数无效");
        if (idempotencyKey != null && idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("幂等键过长");
        }
    }

    private static void validateIdentity(Long campaignId, Long itemId, Long userId, int quantity) {
        if (campaignId == null || itemId == null || userId == null || quantity <= 0) {
            throw new IllegalArgumentException("限时发售预占参数无效");
        }
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static Long parseLong(Object value) {
        if (value == null) return null;
        try { return Long.parseLong(String.valueOf(value)); } catch (RuntimeException ex) { return null; }
    }

    private static Integer parseIntValue(Object value) {
        if (value == null) return null;
        try { return Integer.parseInt(String.valueOf(value)); } catch (RuntimeException ex) { return null; }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public enum ReservationResult {
        RESERVED, ALREADY_RESERVED, DUPLICATE, IDEMPOTENCY_CONFLICT, STOCK_INSUFFICIENT, LIMIT_EXCEEDED, UNAVAILABLE, DISABLED
    }

    public record ActiveReservation(String redisKey, String tenantId, Long campaignId, Long itemId, Long userId,
                                    int quantity, String reservationKey, long createdAtEpochMs) { }
}

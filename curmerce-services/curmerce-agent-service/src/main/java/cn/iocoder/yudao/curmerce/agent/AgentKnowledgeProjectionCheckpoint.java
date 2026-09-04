package cn.iocoder.yudao.curmerce.agent;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-aggregate monotonic event checkpoint. Redis is the durable production
 * implementation; the local fallback keeps small isolated unit tests free of
 * infrastructure while not being used to claim multi-instance durability.
 */
@Component
public class AgentKnowledgeProjectionCheckpoint {
    private static final String PREFIX = "curmerce:agent:knowledge:v1:projection";
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> MARK_APPLIED = new DefaultRedisScript<>(
            "local current = tonumber(redis.call('get', KEYS[1]) or '0') "
                    + "local incoming = tonumber(ARGV[1]) "
                    + "if incoming > current then redis.call('set', KEYS[1], ARGV[1]); return 1 end "
                    + "return 0", Long.class);

    private final StringRedisTemplate redis;
    private final AgentKnowledgeProjectionProperties properties;
    private final Map<String, Long> localCheckpoints = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> localLocks = new ConcurrentHashMap<>();

    public AgentKnowledgeProjectionCheckpoint() {
        this((StringRedisTemplate) null,
                new AgentKnowledgeProjectionProperties(false, null, null, null, 1_000L, 3L, 120L));
    }

    public AgentKnowledgeProjectionCheckpoint(StringRedisTemplate redis, AgentKnowledgeProjectionProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Autowired
    public AgentKnowledgeProjectionCheckpoint(ObjectProvider<StringRedisTemplate> redis,
                                              AgentKnowledgeProjectionProperties properties) {
        this(redis.getIfAvailable(), properties);
    }

    public ProjectionLease acquire(String source, long aggregateId, long eventId) {
        if (aggregateId <= 0 || eventId <= 0) throw new IllegalArgumentException("Agent projection identity is invalid");
        String key = sourceKey(source, aggregateId);
        if (redis == null) return acquireLocal(key, eventId);
        String token = UUID.randomUUID().toString();
        String lockKey = prefix() + ":lock:" + key;
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, token, Duration.ofSeconds(properties.lockSeconds()));
        if (!Boolean.TRUE.equals(locked)) throw new IllegalStateException("Agent knowledge projection is busy");
        long current;
        try {
            current = number(redis.opsForValue().get(prefix() + ":checkpoint:" + key));
        } catch (RuntimeException ex) {
            release(lockKey, token);
            throw new IllegalStateException("Agent knowledge projection checkpoint is unavailable", ex);
        }
        if (eventId <= current) {
            release(lockKey, token);
            return ProjectionLease.skippedLease();
        }
        return new ProjectionLease() {
            private boolean released;
            @Override public boolean skipped() { return false; }
            @Override public void markApplied() {
                Long marked = redis.execute(MARK_APPLIED,
                        List.of(prefix() + ":checkpoint:" + key), String.valueOf(eventId));
                if (!Long.valueOf(1L).equals(marked)) {
                    throw new IllegalStateException("Agent knowledge projection checkpoint moved forward concurrently");
                }
            }
            @Override public void close() {
                if (!released) {
                    released = true;
                    release(lockKey, token);
                }
            }
        };
    }

    public boolean durable() { return redis != null; }

    private ProjectionLease acquireLocal(String key, long eventId) {
        ReentrantLock lock = localLocks.computeIfAbsent(key, ignored -> new ReentrantLock());
        if (!lock.tryLock()) throw new IllegalStateException("Agent knowledge projection is busy");
        if (eventId <= localCheckpoints.getOrDefault(key, 0L)) {
            lock.unlock();
            return ProjectionLease.skippedLease();
        }
        return new ProjectionLease() {
            private boolean released;
            @Override public boolean skipped() { return false; }
            @Override public void markApplied() { localCheckpoints.put(key, eventId); }
            @Override public void close() { if (!released) { released = true; lock.unlock(); } }
        };
    }

    private void release(String lockKey, String token) {
        try { redis.execute(RELEASE_LOCK, List.of(lockKey), token); }
        catch (RuntimeException ignored) { }
    }

    private String prefix() { return AgentRequestContext.key(PREFIX); }

    private static String sourceKey(String source, long aggregateId) {
        String normalized = source == null || source.isBlank() ? "unknown" : source.trim().toLowerCase();
        return normalized + ':' + aggregateId;
    }

    private static long number(String value) {
        try { return value == null ? 0L : Long.parseLong(value); }
        catch (NumberFormatException ignored) { return 0L; }
    }

    public interface ProjectionLease extends AutoCloseable {
        boolean skipped();
        void markApplied();
        @Override void close();
        static ProjectionLease skippedLease() {
            return new ProjectionLease() {
                @Override public boolean skipped() { return true; }
                @Override public void markApplied() { }
                @Override public void close() { }
            };
        }
    }
}

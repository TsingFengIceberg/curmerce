package cn.iocoder.yudao.curmerce.agent;

import io.micrometer.core.instrument.MeterRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class AgentAuditRecorder {
    private static final String REDIS_KEY = "curmerce:agent:audit:v2";
    private final MeterRegistry metrics;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ConcurrentLinkedDeque<Entry> entries = new ConcurrentLinkedDeque<>();
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AgentAuditJdbcArchive jdbcArchive;
    public AgentAuditRecorder(MeterRegistry metrics) { this(metrics, (StringRedisTemplate) null, null); }

    @Autowired
    public AgentAuditRecorder(MeterRegistry metrics, ObjectProvider<StringRedisTemplate> provider, ObjectMapper objectMapper) {
        this(metrics, provider.getIfAvailable(), objectMapper);
    }

    private AgentAuditRecorder(MeterRegistry metrics, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.metrics = metrics; this.redis = redis; this.objectMapper = objectMapper;
    }
    public void record(String principal, String action, String outcome) {
        String subject = AgentPrincipalHasher.hash(principal);
        entries.addLast(new Entry(Instant.now(), subject, action, outcome));
        while (entries.size() > 1000) entries.pollFirst();
        if (jdbcArchive != null) try { jdbcArchive.append(entries.peekLast()); }
        catch (RuntimeException ignored) {
            metrics.counter("curmerce.agent.audit.archive_failures", "backend", "jdbc").increment();
        }
        if (redis != null && objectMapper != null) try {
            String key = AgentRequestContext.key(REDIS_KEY);
            redis.opsForList().rightPush(key, objectMapper.writeValueAsString(entries.peekLast()));
            redis.opsForList().trim(key, -1000, -1);
            redis.expire(key, java.time.Duration.ofDays(30));
        } catch (Exception ignored) { }
        metrics.counter("curmerce.agent.audit", "action", action, "outcome", outcome).increment();
    }
    public List<Entry> recent(int limit) {
        int safeLimit = Math.min(100, Math.max(1, limit));
        if (jdbcArchive != null) try {
            List<Entry> archived = jdbcArchive.recent(safeLimit);
            if (!archived.isEmpty()) return archived;
        } catch (RuntimeException ignored) { }
        if (redis != null && objectMapper != null) try {
            List<String> values = redis.opsForList().range(AgentRequestContext.key(REDIS_KEY), -safeLimit, -1);
            if (values != null && !values.isEmpty()) return values.stream().map(value -> {
                try { return objectMapper.readValue(value, Entry.class); } catch (Exception ex) { return null; }
            }).filter(java.util.Objects::nonNull).toList();
        } catch (RuntimeException ignored) { }
        return entries.stream().skip(Math.max(0, entries.size() - safeLimit)).toList();
    }
    public record Entry(Instant at, String tenantHash, String principalHash, String action, String outcome) {
        /** Compatibility constructor for callers that predate tenant-aware audit rows. */
        public Entry(Instant at, String principalHash, String action, String outcome) {
            this(at, AgentRequestContext.tenantScope(), principalHash, action, outcome);
        }
    }
}

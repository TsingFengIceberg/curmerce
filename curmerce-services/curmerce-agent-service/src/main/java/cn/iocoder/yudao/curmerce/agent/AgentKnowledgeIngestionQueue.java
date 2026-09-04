package cn.iocoder.yudao.curmerce.agent;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.domain.Range;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded ingestion boundary for product/community projection rebuilds. */
@Component
public class AgentKnowledgeIngestionQueue {
    private static final String STREAM = "curmerce:agent:knowledge:v1:ingestion";
    private static final String GROUP = "curmerce-agent-knowledge";
    private static final String JOB_PREFIX = "curmerce:agent:knowledge:v1:job";
    private static final String LOCK_PREFIX = "curmerce:agent:knowledge:v1:lock";
    private static final String RETRY_ZSET = "curmerce:agent:knowledge:v1:retry";
    private static final String DEAD_LETTER_STREAM = "curmerce:agent:knowledge:v1:dead-letter";
    /** Global registry used only to discover tenant-scoped streams on workers. */
    private static final String TENANT_REGISTRY = "curmerce:agent:knowledge:v1:tenants";
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> SUBMIT =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>("""
                    if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
                    redis.call('HSET', KEYS[1], 'status', 'QUEUED', 'tenantId', ARGV[7], 'source', ARGV[1],
                        'documents', ARGV[2], 'submittedAt', ARGV[3], 'attempts', '0', 'version', ARGV[6])
                    redis.call('EXPIRE', KEYS[1], ARGV[4])
                    redis.call('XADD', KEYS[2], '*', 'jobId', ARGV[5], 'tenantId', ARGV[7])
                    redis.call('SADD', KEYS[3], ARGV[7])
                    return 1
                    """, Long.class);
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> SCHEDULE_RETRY =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>("""
                    if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
                    redis.call('HSET', KEYS[1], 'status', 'RETRY_WAIT', 'attempts', ARGV[1],
                        'error', ARGV[2], 'retryAt', ARGV[3])
                    redis.call('ZADD', KEYS[2], ARGV[4], ARGV[5])
                    return 1
                    """, Long.class);
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> CLAIM_RETRY =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>("""
                    if redis.call('ZREM', KEYS[3], ARGV[1]) == 0 then return 0 end
                    if redis.call('HGET', KEYS[1], 'status') ~= 'RETRY_WAIT' then return 0 end
                    redis.call('HSET', KEYS[1], 'status', 'QUEUED', 'retryAt', '')
                    redis.call('XADD', KEYS[2], '*', 'jobId', ARGV[1],
                        'tenantId', redis.call('HGET', KEYS[1], 'tenantId') or '')
                    return 1
                    """, Long.class);
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> REPLAY_FAILED =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>("""
                    if redis.call('HGET', KEYS[1], 'status') ~= 'FAILED' then return 0 end
                    redis.call('HSET', KEYS[1], 'status', 'QUEUED', 'attempts', '0', 'error', '', 'retryAt', '', 'finishedAt', '', 'deadLettered', '')
                    redis.call('XADD', KEYS[2], '*', 'jobId', ARGV[1],
                        'tenantId', redis.call('HGET', KEYS[1], 'tenantId') or '')
                    return 1
                    """, Long.class);
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> RELEASE_LOCK =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>("""
                    if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
                    return 0
                    """, Long.class);
    private final AgentKnowledgeStore store;
    private final ThreadPoolExecutor executor;
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final MeterRegistry metrics;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final boolean durable;
    private final String consumer = "consumer-" + UUID.randomUUID();
    private final Map<String, String> localJobTenants = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final long retryBaseDelayMs;
    private final long retryMaxDelayMs;
    private final long pendingIdleSeconds;
    private final long processingLeaseSeconds;

    public AgentKnowledgeIngestionQueue(AgentKnowledgeStore store,
                                       @Value("${curmerce.agent.knowledge-queue-capacity:10}") int capacity,
                                       @Value("${curmerce.agent.knowledge-worker-threads:1}") int workers,
                                       ObjectProvider<MeterRegistry> metricsProvider) {
        this(store, capacity, workers, metricsProvider.getIfAvailable(), null, null, 3, 500, 30000, 30, 120);
    }

    @Autowired
    public AgentKnowledgeIngestionQueue(AgentKnowledgeStore store,
                                       @Value("${curmerce.agent.knowledge-queue-capacity:10}") int capacity,
                                       @Value("${curmerce.agent.knowledge-worker-threads:1}") int workers,
                                       ObjectProvider<MeterRegistry> metricsProvider,
                                       ObjectProvider<StringRedisTemplate> redisProvider,
                                       ObjectMapper objectMapper,
                                       @Value("${curmerce.agent.knowledge-max-attempts:3}") int maxAttempts,
                                       @Value("${curmerce.agent.knowledge-retry-base-delay-ms:500}") long retryBaseDelayMs,
                                       @Value("${curmerce.agent.knowledge-retry-max-delay-ms:30000}") long retryMaxDelayMs,
                                       @Value("${curmerce.agent.knowledge-pending-idle-seconds:30}") long pendingIdleSeconds,
                                       @Value("${curmerce.agent.knowledge-processing-lease-seconds:120}") long processingLeaseSeconds) {
        this(store, capacity, workers, metricsProvider.getIfAvailable(), redisProvider.getIfAvailable(), objectMapper,
                maxAttempts, retryBaseDelayMs, retryMaxDelayMs, pendingIdleSeconds, processingLeaseSeconds);
    }

    private AgentKnowledgeIngestionQueue(AgentKnowledgeStore store, int capacity, int workers,
                                         MeterRegistry metrics, StringRedisTemplate redis,
                                         ObjectMapper objectMapper, int maxAttempts,
                                         long retryBaseDelayMs, long retryMaxDelayMs,
                                         long pendingIdleSeconds, long processingLeaseSeconds) {
        this.store = store;
        this.metrics = metrics;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.durable = redis != null && objectMapper != null;
        this.maxAttempts = Math.max(1, Math.min(maxAttempts, 10));
        this.retryBaseDelayMs = Math.max(1L, retryBaseDelayMs);
        this.retryMaxDelayMs = Math.max(this.retryBaseDelayMs, retryMaxDelayMs);
        this.pendingIdleSeconds = Math.max(1L, Math.min(pendingIdleSeconds, 3600L));
        this.processingLeaseSeconds = Math.max(10L, Math.min(processingLeaseSeconds, 3600L));
        this.executor = new ThreadPoolExecutor(Math.max(1, Math.min(workers, 8)),
                Math.max(1, Math.min(workers, 8)), 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(1, Math.min(capacity, 100))),
                new ThreadPoolExecutor.AbortPolicy());
        if (metrics != null) {
            io.micrometer.core.instrument.Gauge.builder("curmerce.agent.knowledge.queue.depth", this,
                    AgentKnowledgeIngestionQueue::queueDepth).register(metrics);
            io.micrometer.core.instrument.Gauge.builder("curmerce.agent.knowledge.queue.pending", this,
                    AgentKnowledgeIngestionQueue::pendingDepth).register(metrics);
            io.micrometer.core.instrument.Gauge.builder("curmerce.agent.knowledge.queue.retry.waiting", this,
                    AgentKnowledgeIngestionQueue::retryWaiting).register(metrics);
            io.micrometer.core.instrument.Gauge.builder("curmerce.agent.knowledge.queue.dead.letter.depth", this,
                    AgentKnowledgeIngestionQueue::deadLetterDepth).register(metrics);
        }
    }

    public Job submit(String source, List<AgentKnowledgeStore.SourceDocument> documents) {
        String id = UUID.randomUUID().toString();
        Instant submitted = Instant.now();
        List<AgentKnowledgeStore.SourceDocument> safeDocuments = documents == null ? List.of() : List.copyOf(documents);
        long version = store.nextSourceVersion(source);
        Job queued = new Job(id, "QUEUED", 0, null, submitted, null, 0, null, version);
        jobs.put(id, queued);
        String tenant = AgentRequestContext.tenantId();
        localJobTenants.put(id, tenant);
        if (durable) {
            try {
                String documentsJson = objectMapper.writeValueAsString(safeDocuments);
                Long accepted = redis.execute(SUBMIT, List.of(jobKey(id), stream(), TENANT_REGISTRY),
                        source == null ? "unknown" : source.trim(), documentsJson, submitted.toString(), "604800", id,
                        String.valueOf(version), tenant);
                if (!Long.valueOf(1L).equals(accepted)) throw new IllegalStateException("duplicate knowledge job");
                increment("accepted");
                return queued;
            } catch (Exception ex) {
                jobs.put(id, new Job(id, "FAILED", 0, "知识索引队列暂时不可用", submitted, Instant.now()));
                increment("rejected");
                return jobs.get(id);
            }
        }
        try {
            executor.execute(() -> run(id, source, safeDocuments, tenant));
            increment("accepted");
            return jobs.get(id);
        } catch (RuntimeException ex) {
            jobs.put(id, new Job(id, "FAILED", 0, "知识索引队列已满", submitted, Instant.now()));
            increment("rejected");
            return jobs.get(id);
        }
    }

    public Job status(String id) {
        if (id == null || id.isBlank()) return null;
        if (durable) {
            try {
                Map<Object, Object> fields = redis.opsForHash().entries(jobKey(id));
                if (!fields.isEmpty()) {
                    String owner = String.valueOf(fields.getOrDefault("tenantId", ""));
                    if (owner.isBlank() || !AgentRequestContext.tenantId().equals(AgentRequestContext.normalizeTenant(owner))) {
                        return null;
                    }
                    return toJob(id, fields);
                }
            } catch (IllegalArgumentException ex) {
                // Invalid or missing tenant metadata must never fall through
                // to the process-local map or the default tenant namespace.
                return null;
            } catch (RuntimeException ignored) { }
        }
        String owner = localJobTenants.get(id);
        if (owner == null || !AgentRequestContext.tenantId().equals(owner)) return null;
        return jobs.get(id);
    }

    public boolean durable() { return durable; }
    public int queueDepth() {
        if (durable) {
            try { Long size = redis.opsForStream().size(stream()); return size == null ? 0 : size.intValue(); }
            catch (RuntimeException ignored) { return -1; }
        }
        return executor.getQueue().size();
    }

    public int pendingDepth() {
        if (!durable) return executor.getQueue().size();
        try {
            PendingMessages pending = redis.opsForStream().pending(stream(), group(), Range.unbounded(), 10000);
            return pending == null ? 0 : pending.size();
        } catch (RuntimeException ignored) { return -1; }
    }

    public int retryWaiting() {
        if (!durable) return 0;
        try { Long value = redis.opsForZSet().zCard(retryZset()); return value == null ? 0 : value.intValue(); }
        catch (RuntimeException ignored) { return -1; }
    }

    public int deadLetterDepth() {
        if (!durable) return 0;
        try { Long value = redis.opsForStream().size(deadLetterStream()); return value == null ? 0 : value.intValue(); }
        catch (RuntimeException ignored) { return -1; }
    }

    /** Returns prompt-free metadata for failed indexing jobs. */
    public List<DeadLetter> deadLetters(int limit) {
        if (!durable) return List.of();
        int safeLimit = Math.min(100, Math.max(1, limit));
        try {
            List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                    StreamReadOptions.empty().count(safeLimit),
                    StreamOffset.create(deadLetterStream(), ReadOffset.from("0-0")));
            if (records == null) return List.of();
            return records.stream().map(record -> {
                Map<Object, Object> fields = record.getValue();
                return new DeadLetter(record.getId().getValue(), String.valueOf(fields.getOrDefault("jobId", "")),
                        String.valueOf(fields.getOrDefault("source", "unknown")), intValue(fields.get("attempts")),
                        String.valueOf(fields.getOrDefault("error", "知识索引失败")), parseOptionalInstant(fields.get("failedAt")));
            }).toList();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("知识索引死信暂时不可用", ex);
        }
    }

    /** Retry a failed durable indexing job without losing its original payload. */
    public boolean retry(String id) {
        if (!durable || id == null || id.isBlank()) return false;
        try {
            Map<Object, Object> fields = redis.opsForHash().entries(jobKey(id));
            if (fields.isEmpty() || !"FAILED".equals(String.valueOf(fields.get("status")))) return false;
            Long result = redis.execute(REPLAY_FAILED, List.of(jobKey(id), stream()), id);
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException ex) { return false; }
    }

    @Scheduled(fixedDelayString = "${curmerce.agent.knowledge-poll-ms:500}")
    public void pollDurable() {
        if (!durable) return;
        try {
            Set<String> tenants = redis.opsForSet().members(TENANT_REGISTRY);
            if (tenants == null || tenants.isEmpty()) return;
            for (String tenant : tenants) {
                if (tenant == null || tenant.isBlank()) continue;
                try (AgentRequestContext.Scope ignored = AgentRequestContext.open("knowledge-worker", tenant)) {
                    pollTenant();
                } catch (IllegalArgumentException ex) {
                    increment("invalid-tenant");
                }
            }
        } catch (RuntimeException ex) { increment("poll-errors"); }
    }

    private void pollTenant() {
        ensureGroup();
        releaseDueRetries();
        recoverPending();
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                Consumer.from(group(), consumer), StreamReadOptions.empty().count(4),
                StreamOffset.create(stream(), ReadOffset.lastConsumed()));
        if (records != null) records.forEach(this::processDurable);
    }

    private void releaseDueRetries() {
        Set<String> due = redis.opsForZSet().rangeByScore(retryZset(), Double.NEGATIVE_INFINITY,
                System.currentTimeMillis(), 0, 100);
        if (due == null || due.isEmpty()) return;
        for (String id : due) {
            Long claimed = redis.execute(CLAIM_RETRY, List.of(jobKey(id), stream(), retryZset()), id);
            if (Long.valueOf(1L).equals(claimed)) increment("retry-released");
        }
    }

    private void run(String id, String source, List<AgentKnowledgeStore.SourceDocument> documents) {
        run(id, source, documents, AgentRequestContext.tenantId());
    }

    private void run(String id, String source, List<AgentKnowledgeStore.SourceDocument> documents, String tenant) {
        try (AgentRequestContext.Scope ignored = AgentRequestContext.open("knowledge-worker", tenant)) {
        Job current = jobs.get(id);
        jobs.put(id, new Job(id, "PROCESSING", 0, null, current.submittedAt(), null, current.attempts(), null, current.version()));
        try {
            int count = store.replaceSource(source, documents, current.version());
            jobs.put(id, new Job(id, "COMPLETED", count, null, current.submittedAt(), Instant.now(), current.attempts(), null, current.version()));
            increment("completed");
        } catch (RuntimeException ex) {
            jobs.put(id, new Job(id, "FAILED", 0, "知识索引失败", current.submittedAt(), Instant.now()));
            increment("failed");
        }
        }
    }

    private void processDurable(MapRecord<String, Object, Object> record) {
        String recordTenant = String.valueOf(record.getValue().getOrDefault("tenantId", ""));
        if (recordTenant.isBlank()) {
            // A missing tenant is a poison record. Never project it into the
            // default namespace where it could mix data from old producers.
            redis.opsForStream().acknowledge(stream(), group(), record.getId());
            increment("missing-tenant");
            return;
        }
        try {
            if (!AgentRequestContext.tenantId().equals(AgentRequestContext.normalizeTenant(recordTenant))) {
                redis.opsForStream().acknowledge(stream(), group(), record.getId());
                increment("tenant-mismatch");
                return;
            }
        } catch (IllegalArgumentException ex) {
            redis.opsForStream().acknowledge(stream(), group(), record.getId());
            increment("invalid-tenant");
            return;
        }
        String id = String.valueOf(record.getValue().get("jobId"));
        String lock = lockKey(id);
        Boolean acquired;
        try { acquired = redis.opsForValue().setIfAbsent(lock, consumer, java.time.Duration.ofSeconds(processingLeaseSeconds)); }
        catch (RuntimeException ex) { increment("poll-errors"); return; }
        if (!Boolean.TRUE.equals(acquired)) return;
        boolean projectionCommitted = false;
        try {
            Map<Object, Object> fields = redis.opsForHash().entries(jobKey(id));
            if (fields.isEmpty() || "COMPLETED".equals(String.valueOf(fields.get("status")))) {
                redis.opsForStream().acknowledge(stream(), group(), record.getId());
                return;
            }
            if ("RETRY_WAIT".equals(String.valueOf(fields.get("status")))) {
                Instant retryAt = parseOptionalInstant(fields.get("retryAt"));
                if (retryAt != null && retryAt.isAfter(Instant.now())) {
                    // The retry ZSET owns the next attempt. An old stream
                    // delivery must not execute the projection early.
                    redis.opsForStream().acknowledge(stream(), group(), record.getId());
                    return;
                }
            }
            if ("FAILED".equals(String.valueOf(fields.get("status")))) {
                if ("1".equals(String.valueOf(fields.getOrDefault("deadLettered", "0")))) {
                    redis.opsForStream().acknowledge(stream(), group(), record.getId());
                } else if (appendDeadLetter(id, fields, intValue(fields.get("attempts")),
                        new IllegalStateException(String.valueOf(fields.getOrDefault("error", "知识索引失败"))))) {
                    redis.opsForHash().put(jobKey(id), "deadLettered", "1");
                    redis.opsForStream().acknowledge(stream(), group(), record.getId());
                }
                return;
            }
            redis.opsForHash().put(jobKey(id), "status", "PROCESSING");
            String source = String.valueOf(fields.getOrDefault("source", "unknown"));
            List<AgentKnowledgeStore.SourceDocument> docs = objectMapper.readValue(
                    String.valueOf(fields.getOrDefault("documents", "[]")),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, AgentKnowledgeStore.SourceDocument.class));
            long version = longValue(fields.get("version"));
            int count = store.replaceSource(source, docs, version);
            projectionCommitted = true;
            redis.opsForHash().putAll(jobKey(id), Map.of("status", "COMPLETED", "documentCount", String.valueOf(count), "finishedAt", Instant.now().toString(), "retryAt", ""));
            redis.opsForStream().acknowledge(stream(), group(), record.getId());
            increment("completed");
        } catch (Exception ex) {
            if (projectionCommitted) {
                // The knowledge projection is the source of truth for this
                // job. Leave the stream entry pending so a later delivery can
                // repair Redis metadata without rebuilding the source early.
                increment("post-commit-errors");
                return;
            }
            try {
                Map<Object, Object> fields = redis.opsForHash().entries(jobKey(id));
                int attempts = Integer.parseInt(String.valueOf(fields.getOrDefault("attempts", "0"))) + 1;
                if (attempts >= maxAttempts) {
                    redis.opsForHash().putAll(jobKey(id), Map.of("status", "FAILED", "attempts", String.valueOf(attempts), "error", "知识索引失败", "finishedAt", Instant.now().toString()));
                    boolean deadLettered = appendDeadLetter(id, fields, attempts, ex);
                    if (deadLettered) redis.opsForHash().put(jobKey(id), "deadLettered", "1");
                    redis.opsForZSet().remove(retryZset(), id);
                    if (deadLettered) redis.opsForStream().acknowledge(stream(), group(), record.getId());
                    increment("failed");
                } else {
                    long retryAt = System.currentTimeMillis() + retryDelayMs(attempts);
                    redis.execute(SCHEDULE_RETRY, List.of(jobKey(id), retryZset()),
                            String.valueOf(attempts), "知识索引暂时失败", Instant.ofEpochMilli(retryAt).toString(),
                            String.valueOf(retryAt), id);
                    redis.opsForStream().acknowledge(stream(), group(), record.getId());
                    increment("retried");
                }
            } catch (RuntimeException ignored) { increment("poll-errors"); }
        } finally {
            // A timed-out worker can finish after another instance acquired a
            // replacement lease. Conditional deletion must not release that
            // newer worker's lock.
            try { redis.execute(RELEASE_LOCK, List.of(lock), consumer); }
            catch (RuntimeException ignored) { increment("lock-release-errors"); }
        }
    }

    private void recoverPending() {
        PendingMessages pending = redis.opsForStream().pending(stream(), group(), Range.unbounded(), 20);
        if (pending == null || pending.isEmpty()) return;
        List<org.springframework.data.redis.connection.stream.RecordId> ids = pending.stream()
                .filter(item -> item.getElapsedTimeSinceLastDelivery().compareTo(java.time.Duration.ofSeconds(pendingIdleSeconds)) > 0)
                .map(PendingMessage::getId).toList();
        if (!ids.isEmpty()) {
            List<MapRecord<String, Object, Object>> claimed = redis.opsForStream().claim(stream(), group(), consumer,
                    java.time.Duration.ofSeconds(pendingIdleSeconds), ids.toArray(org.springframework.data.redis.connection.stream.RecordId[]::new));
            if (claimed != null) claimed.forEach(this::processDurable);
        }
    }

    private void ensureGroup() {
        try { redis.opsForStream().createGroup(stream(), ReadOffset.from("0-0"), group()); }
        catch (RuntimeException ignored) { }
    }

    private boolean appendDeadLetter(String id, Map<Object, Object> fields, int attempts, Exception failure) {
        try {
            if ("1".equals(String.valueOf(fields.getOrDefault("deadLettered", "0")))) return true;
            Map<String, String> dead = new LinkedHashMap<>();
            dead.put("jobId", id);
            dead.put("source", String.valueOf(fields.getOrDefault("source", "unknown")));
            dead.put("attempts", String.valueOf(attempts));
            dead.put("error", failure.getMessage() == null ? "知识索引失败" : failure.getMessage());
            dead.put("failedAt", Instant.now().toString());
            redis.opsForStream().add(deadLetterStream(), dead);
            redis.opsForStream().trim(deadLetterStream(), 10000L);
            return true;
        } catch (RuntimeException ignored) { increment("dead-letter-errors"); return false; }
    }

    private Job toJob(String id, Map<Object, Object> fields) {
        Instant submitted = parseInstant(fields.get("submittedAt"));
        Instant finished = parseInstant(fields.get("finishedAt"));
        return new Job(id, String.valueOf(fields.getOrDefault("status", "QUEUED")), intValue(fields.get("documentCount")),
                fields.get("error") == null ? null : String.valueOf(fields.get("error")), submitted, finished,
                intValue(fields.get("attempts")), parseOptionalInstant(fields.get("retryAt")), longValue(fields.get("version")));
    }
    private static Instant parseInstant(Object value) {
        try { return value == null ? Instant.EPOCH : Instant.parse(String.valueOf(value)); }
        catch (RuntimeException ex) { return Instant.EPOCH; }
    }
    private static Instant parseOptionalInstant(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return Instant.parse(String.valueOf(value)); } catch (RuntimeException ex) { return null; }
    }
    private static int intValue(Object value) {
        try { return value == null ? 0 : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ex) { return 0; }
    }
    private static long longValue(Object value) {
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ex) { return 0L; }
    }
    private String stream() { return AgentRequestContext.key(STREAM); }
    private String group() { return GROUP + "-" + AgentRequestContext.tenantScope(); }
    private String retryZset() { return AgentRequestContext.key(RETRY_ZSET); }
    private String deadLetterStream() { return AgentRequestContext.key(DEAD_LETTER_STREAM); }
    private String jobKey(String id) { return AgentRequestContext.key(JOB_PREFIX) + ":" + id; }
    private String lockKey(String id) { return AgentRequestContext.key(LOCK_PREFIX) + ":" + id; }
    private long retryDelayMs(int attempts) {
        int exponent = Math.max(0, Math.min(attempts - 1, 20));
        long multiplier = 1L << exponent;
        try { return Math.min(retryMaxDelayMs, Math.multiplyExact(retryBaseDelayMs, multiplier)); }
        catch (ArithmeticException ex) { return retryMaxDelayMs; }
    }
    private void increment(String outcome) {
        if (metrics != null) metrics.counter("curmerce.agent.knowledge.ingestion", "outcome", outcome).increment();
    }

    @PreDestroy
    public void shutdown() { executor.shutdownNow(); }

    public record Job(String id, String status, int documentCount, String error, Instant submittedAt, Instant finishedAt,
                      int attempts, Instant retryAt, long version) {
        public Job(String id, String status, int documentCount, String error, Instant submittedAt, Instant finishedAt) {
            this(id, status, documentCount, error, submittedAt, finishedAt, 0, null, 0L);
        }
    }
    public record DeadLetter(String entryId, String jobId, String source, int attempts, String error, Instant failedAt) { }
}

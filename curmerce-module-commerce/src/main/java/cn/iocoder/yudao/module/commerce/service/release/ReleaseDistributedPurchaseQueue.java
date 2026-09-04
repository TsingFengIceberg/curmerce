package cn.iocoder.yudao.module.commerce.service.release;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePurchaseReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePurchaseRespVO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Range;

/**
 * Redis Stream consumer-group backend for multi-instance flash-sale traffic.
 * The local queue remains available as a development fallback; this component
 * is selected explicitly through curmerce.release.distributed-queue-enabled.
 */
@Component
public class ReleaseDistributedPurchaseQueue {
    private static final String STREAM = "curmerce:release:purchase:v1:stream";
    private static final String DEAD_LETTER_STREAM = "curmerce:release:purchase:v1:dead-letter";
    private static final String GROUP = "curmerce-release-purchase";
    private static final String TICKET_PREFIX = "curmerce:release:purchase:v1:ticket:";
    private static final String DEDUPE_PREFIX = "curmerce:release:purchase:v1:dedupe:";
    private static final String PROCESS_LOCK_PREFIX = "curmerce:release:purchase:v1:processing:";
    private static final String RETRY_ZSET = "curmerce:release:purchase:v1:retry";
    private static final String TENANT_REGISTRY = "curmerce:release:purchase:v1:tenants";
    private static final DefaultRedisScript<String> ENQUEUE = new DefaultRedisScript<>("""
            local existing = redis.call('GET', KEYS[3])
            if existing then return existing end
            if redis.call('EXISTS', KEYS[1]) == 1 then return '' end
            redis.call('HSET', KEYS[1],
                'owner', ARGV[1], 'tenantId', ARGV[7], 'status', 'QUEUED', 'attempts', '0',
                'acceptedAt', ARGV[2], 'userId', ARGV[1], 'request', ARGV[3])
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            redis.call('SET', KEYS[3], ARGV[5], 'EX', ARGV[4])
            redis.call('XADD', KEYS[2], 'MAXLEN', '~', ARGV[6], '*',
                'ticket', ARGV[5], 'tenantId', ARGV[7], 'userId', ARGV[1], 'request', ARGV[3])
            return ARGV[5]
            """, String.class);
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end
            """, Long.class);
    private static final DefaultRedisScript<Long> REPLAY_FAILED = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'status') ~= 'FAILED' then return 0 end
            redis.call('HSET', KEYS[1], 'status', 'QUEUED', 'attempts', '0', 'error', '', 'result', '', 'failedAt', '', 'retryAt', '', 'deadLettered', '')
            redis.call('XADD', KEYS[2], 'MAXLEN', '~', 10000, '*',
                'ticket', ARGV[1], 'tenantId', redis.call('HGET', KEYS[1], 'tenantId') or '', 'userId', ARGV[2], 'request', ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> SCHEDULE_RETRY = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            redis.call('HSET', KEYS[1], 'status', 'RETRY_WAIT', 'attempts', ARGV[1],
                'error', ARGV[2], 'retryAt', ARGV[3])
            redis.call('ZADD', KEYS[2], ARGV[4], ARGV[5])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> CLAIM_RETRY = new DefaultRedisScript<>("""
            if redis.call('ZREM', KEYS[3], ARGV[1]) == 0 then return 0 end
            if redis.call('HGET', KEYS[1], 'status') ~= 'RETRY_WAIT' then return 0 end
            local request = redis.call('HGET', KEYS[1], 'request')
            local userId = redis.call('HGET', KEYS[1], 'userId')
            if not request or not userId then return 0 end
            redis.call('HSET', KEYS[1], 'status', 'QUEUED', 'retryAt', '')
            redis.call('XADD', KEYS[2], 'MAXLEN', '~', ARGV[2], '*',
                'ticket', ARGV[1], 'tenantId', redis.call('HGET', KEYS[1], 'tenantId') or '', 'userId', userId, 'request', request)
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> REMOVE_RETRY = new DefaultRedisScript<>(
            "return redis.call('ZREM', KEYS[1], ARGV[1])", Long.class);

    private final ReleaseService service;
    private final StringRedisTemplate redis;
    private final MeterRegistry metrics;
    private final boolean enabled;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ReleaseQueueFailureInjector failureInjector;
    private final String consumer = "consumer-" + UUID.randomUUID();
    @Value("${curmerce.release.distributed-ticket-ttl-seconds:3600}")
    private long ticketTtlSeconds = 3600L;
    @Value("${curmerce.release.distributed-max-attempts:3}")
    private int maxAttempts = 3;
    @Value("${curmerce.release.distributed-pending-idle-seconds:30}")
    private long pendingIdleSeconds = 30L;
    @Value("${curmerce.release.distributed-stream-max-length:10000}")
    private long streamMaxLength = 10000L;
    @Value("${curmerce.release.distributed-retry-base-delay-ms:250}")
    private long retryBaseDelayMs = 250L;
    @Value("${curmerce.release.distributed-retry-max-delay-ms:30000}")
    private long retryMaxDelayMs = 30000L;
    @Value("${curmerce.release.distributed-processing-lease-seconds:120}")
    private long processingLeaseSeconds = 120L;

    public ReleaseDistributedPurchaseQueue(ReleaseService service, StringRedisTemplate redis,
                                           ObjectProvider<MeterRegistry> metricsProvider,
                                           @Value("${curmerce.release.distributed-queue-enabled:false}") boolean enabled) {
        this.service = service;
        this.redis = redis;
        this.metrics = metricsProvider.getIfAvailable();
        this.enabled = enabled;
        if (this.metrics != null) {
            io.micrometer.core.instrument.Gauge.builder("curmerce.release.distributed.stream.depth", this,
                            ReleaseDistributedPurchaseQueue::streamDepth)
                    .description("Retained Redis Stream entries for release purchases").register(this.metrics);
            io.micrometer.core.instrument.Gauge.builder("curmerce.release.distributed.pending", this,
                            ReleaseDistributedPurchaseQueue::pendingDepth)
                    .description("Unacknowledged Redis Stream entries in the release consumer group").register(this.metrics);
        }
    }

    public boolean enabled() { return enabled; }

    /** Number of entries currently retained in the stream, or -1 when Redis is unavailable. */
    public long streamDepth() {
        if (!enabled) return 0L;
        try { return redis.opsForStream().size(stream(tenantScope())); }
        catch (RuntimeException ex) { return -1L; }
    }

    /** Number of unacknowledged records in the consumer group. */
    public long pendingDepth() {
        if (!enabled) return 0L;
        try {
            PendingMessages pending = redis.opsForStream().pending(stream(tenantScope()), group(tenantScope()), Range.unbounded(), 10000);
            return pending == null ? 0L : pending.size();
        } catch (RuntimeException ex) {
            return -1L;
        }
    }

    /** Number of tickets waiting for their next transient retry. */
    public long retryWaiting() {
        if (!enabled) return 0L;
        try {
            Long value = redis.opsForZSet().zCard(retryZset(tenantScope()));
            return value == null ? 0L : value;
        } catch (RuntimeException ex) {
            return -1L;
        }
    }

    /** Number of terminal entries retained for operator inspection. */
    public long deadLetterDepth() {
        if (!enabled) return 0L;
        try {
            Long value = redis.opsForStream().size(deadLetterStream(tenantScope()));
            return value == null ? 0L : value;
        } catch (RuntimeException ex) {
            return -1L;
        }
    }

    /**
     * Returns a bounded, prompt-free dead-letter view for operators. The
     * original request is deliberately not returned because it may contain
     * user supplied data; the ticket can still be replayed by its id.
     */
    public List<DeadLetter> deadLetters(int limit) {
        if (!enabled) return List.of();
        int safeLimit = Math.min(100, Math.max(1, limit));
        try {
            List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                    org.springframework.data.redis.connection.stream.StreamReadOptions.empty().count(safeLimit),
                    StreamOffset.create(deadLetterStream(tenantScope()), ReadOffset.from("0-0")));
            if (records == null) return List.of();
            return records.stream().map(ReleaseDistributedPurchaseQueue::toDeadLetter).toList();
        } catch (RuntimeException ex) {
            throw new QueueUnavailableException("限时发售死信暂时不可用");
        }
    }

    /** Replays a failed ticket referenced by a dead-letter entry. */
    public boolean replayDeadLetter(String entryId) {
        if (!enabled) throw new IllegalStateException("分布式限时发售队列未启用");
        if (entryId == null || entryId.isBlank()) throw new IllegalArgumentException("死信编号不能为空");
        try {
            List<MapRecord<String, Object, Object>> records = redis.opsForStream().range(
                    deadLetterStream(tenantScope()), org.springframework.data.domain.Range.closed(entryId.trim(), entryId.trim()));
            if (records == null || records.isEmpty()) return false;
            Map<Object, Object> values = records.get(0).getValue();
            String ticket = String.valueOf(values.getOrDefault("ticket", ""));
            String userId = String.valueOf(values.getOrDefault("userId", ""));
            String request = String.valueOf(values.getOrDefault("request", ""));
            if (ticket.isBlank() || userId.isBlank() || request.isBlank()) return false;
            Long result = redis.execute(REPLAY_FAILED, List.of(ticketKey(tenantScope(), ticket), stream(tenantScope())),
                    ticket, userId, request);
            if (Long.valueOf(1L).equals(result)) increment("curmerce.release.distributed.operator-replays");
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException ex) {
            throw new QueueUnavailableException("限时发售死信重放暂时不可用");
        }
    }

    public QueueSnapshot snapshot() {
        return new QueueSnapshot(enabled, streamDepth(), pendingDepth(), retryWaiting(), deadLetterDepth());
    }

    public String enqueue(Long userId, ReleasePurchaseReqVO request) {
        if (!enabled) throw new IllegalStateException("分布式限时发售队列未启用");
        String ticket = UUID.randomUUID().toString();
        String tenant = tenantScope();
        Instant acceptedAt = Instant.now();
        try {
            String requestJson = JsonUtils.toJsonString(request);
            String idempotencyKey = request.getIdempotencyKey() == null ? "" : request.getIdempotencyKey().trim();
            String dedupe = idempotencyKey.isBlank()
                    ? dedupeKey(tenant, ticket)
                    : DEDUPE_PREFIX + tenant + ":" + userId + ":" + request.getItemId() + ":" + digest(idempotencyKey);
            failIfConfigured(ReleaseQueueFailureInjector.Point.ENQUEUE);
            String result = redis.execute(ENQUEUE, List.of(ticketKey(tenant, ticket), stream(tenant), dedupe, TENANT_REGISTRY),
                    String.valueOf(userId), acceptedAt.toString(), requestJson,
                    String.valueOf(Math.max(60L, ticketTtlSeconds)), ticket,
                    String.valueOf(Math.max(100L, streamMaxLength)), tenant);
            if (result == null || result.isBlank()) throw new QueueUnavailableException("限时发售排队票据创建失败");
            increment("curmerce.release.distributed.accepted");
            if (!ticket.equals(result)) increment("curmerce.release.distributed.duplicates");
            return result;
        } catch (RuntimeException ex) {
            try {
                // Cleanup is best-effort. A Redis outage must still surface
                // as the stable queue-unavailable contract rather than being
                // replaced by a second cleanup exception.
                redis.delete(ticketKey(tenant, ticket));
            } catch (RuntimeException ignored) { }
            increment("curmerce.release.distributed.rejected");
            throw new QueueUnavailableException("限时发售排队服务暂时不可用");
        }
    }

    public ReleasePurchaseQueue.Ticket status(String ticket, Long userId) {
        if (!enabled) throw new IllegalStateException("分布式限时发售队列未启用");
        validateTicket(ticket);
        Map<Object, Object> fields = redis.opsForHash().entries(ticketKey(tenantScope(), ticket));
        if (fields.isEmpty()) throw new IllegalArgumentException("排队票据不存在或已过期");
        if (userId == null || !String.valueOf(userId).equals(String.valueOf(fields.get("owner")))) {
            throw new ReleasePurchaseQueue.TicketAccessDeniedException("无权查询该排队票据");
        }
        String status = String.valueOf(fields.getOrDefault("status", "QUEUED"));
        ReleasePurchaseRespVO result = parseResult(fields.get("result"));
        String error = fields.get("error") == null ? null : String.valueOf(fields.get("error"));
        Instant acceptedAt = Instant.parse(String.valueOf(fields.get("acceptedAt")));
        int attempts = parseInt(fields.get("attempts"));
        Instant retryAt = parseInstant(fields.get("retryAt"));
        return new ReleasePurchaseQueue.Ticket(status, result, error, acceptedAt, attempts, retryAt);
    }

    /**
     * Requeue a terminal ticket atomically with its Stream entry. The owner
     * must explicitly retry; this prevents an operator or another user from
     * replaying a purchase under the wrong identity.
     */
    public boolean retry(String ticket, Long userId) {
        if (!enabled) throw new IllegalStateException("分布式限时发售队列未启用");
        validateTicket(ticket);
        Map<Object, Object> fields = redis.opsForHash().entries(ticketKey(tenantScope(), ticket));
        if (fields.isEmpty()) throw new IllegalArgumentException("排队票据不存在或已过期");
        if (userId == null || !String.valueOf(userId).equals(String.valueOf(fields.get("owner")))) {
            throw new ReleasePurchaseQueue.TicketAccessDeniedException("无权重放该排队票据");
        }
        String request = fields.get("request") == null ? "" : String.valueOf(fields.get("request"));
        String owner = fields.get("userId") == null ? String.valueOf(userId) : String.valueOf(fields.get("userId"));
        if (request.isBlank()) throw new IllegalArgumentException("排队票据缺少原始请求，无法重放");
        Long result = redis.execute(REPLAY_FAILED, List.of(ticketKey(tenantScope(), ticket), stream(tenantScope())), ticket, owner, request);
        return Long.valueOf(1L).equals(result);
    }

    @Scheduled(fixedDelayString = "${curmerce.release.distributed-poll-ms:100}")
    public void poll() {
        if (!enabled) return;
        Set<String> scopes = tenantScopes();
        if (scopes.isEmpty()) scopes = Set.of(tenantScope());
        for (String scope : scopes) runInTenant(scope, this::pollTenant);
    }

    private void pollTenant() {
        try {
            ensureGroup();
            releaseDueRetries();
            recoverPending();
            String scope = tenantScope();
            List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                    Consumer.from(group(scope), consumer), StreamReadOptions.empty().count(10),
                    StreamOffset.create(stream(scope), ReadOffset.lastConsumed()));
            if (records == null) return;
            for (MapRecord<String, Object, Object> record : records) process(record);
        } catch (RuntimeException ignored) {
            increment("curmerce.release.distributed.poll-errors");
        }
    }

    private void releaseDueRetries() {
        String scope = tenantScope();
        Set<String> due = redis.opsForZSet().rangeByScore(retryZset(scope), Double.NEGATIVE_INFINITY,
                System.currentTimeMillis(), 0, 100);
        if (due == null || due.isEmpty()) return;
        for (String ticket : due) {
            Long claimed = redis.execute(CLAIM_RETRY,
                    List.of(ticketKey(scope, ticket), stream(scope), retryZset(scope)), ticket,
                    String.valueOf(Math.max(100L, streamMaxLength)));
            if (Long.valueOf(1L).equals(claimed)) increment("curmerce.release.distributed.retry-released");
        }
    }

    private void recoverPending() {
        String scope = tenantScope();
        PendingMessages pending = redis.opsForStream().pending(stream(scope), group(scope), Range.unbounded(), 10);
        if (pending == null || pending.isEmpty()) return;
        Duration pendingClaimDuration = pendingClaimDuration();
        List<org.springframework.data.redis.connection.stream.RecordId> reclaimable = pending.stream()
                .filter(item -> item.getElapsedTimeSinceLastDelivery().compareTo(pendingClaimDuration) > 0)
                .map(PendingMessage::getId).toList();
        if (reclaimable.isEmpty()) return;
        List<MapRecord<String, Object, Object>> claimed = redis.opsForStream().claim(stream(scope), group(scope), consumer,
                pendingClaimDuration, reclaimable.toArray(org.springframework.data.redis.connection.stream.RecordId[]::new));
        if (claimed != null) for (MapRecord<String, Object, Object> record : claimed) process(record);
    }

    /** The Redis pending filter and XCLAIM must use one threshold. Otherwise a
     * configured non-default idle window can either never recover work or
     * reclaim it earlier than the operator configured. */
    Duration pendingClaimDuration() {
        return Duration.ofSeconds(Math.max(1L, pendingIdleSeconds));
    }

    private void ensureGroup() {
        String scope = tenantScope();
        try { redis.opsForStream().createGroup(stream(scope), ReadOffset.from("0-0"), group(scope)); }
        catch (RuntimeException ignored) { /* BUSYGROUP means another instance created it */ }
    }

    private void process(MapRecord<String, Object, Object> record) {
        Map<Object, Object> values = record.getValue();
        String ticket = string(values.get("ticket"));
        String requestJson = string(values.get("request"));
        Long userId = parseLong(values.get("userId"));
        String messageTenant = string(values.get("tenantId"));
        if (messageTenant == null || !messageTenant.equals(tenantScope()) || ticket == null || ticket.isBlank()
                || requestJson == null || requestJson.isBlank() || userId == null) {
            if (appendDeadLetter(values, ticket == null ? "unknown" : ticket, userId,
                    requestJson == null ? "" : requestJson, 1,
                    new IllegalArgumentException("排队消息格式无效"))) {
                try { redis.opsForStream().acknowledge(stream(tenantScope()), group(tenantScope()), record.getId()); } catch (RuntimeException ignored) { }
            }
            increment("curmerce.release.distributed.malformed");
            return;
        }
        String lockKey = processingLock(tenantScope(), ticket);
        boolean locked;
        try {
            locked = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey, consumer,
                    Duration.ofSeconds(Math.max(10L, Math.min(processingLeaseSeconds, 3600L)))));
        } catch (RuntimeException ex) {
            increment("curmerce.release.distributed.poll-errors");
            return;
        }
        if (!locked) return;
        boolean purchaseCommitted = false;
        Map<Object, Object> current = Map.of();
        try {
            current = redis.opsForHash().entries(ticketKey(ticket));
            if (current.isEmpty()) {
                // An expired ticket must never be executed after its ownership
                // record disappeared; acknowledge the orphaned stream entry.
                redis.opsForStream().acknowledge(stream(tenantScope()), group(tenantScope()), record.getId());
                increment("curmerce.release.distributed.expired");
                return;
            }
            String currentStatus = String.valueOf(current.getOrDefault("status", "QUEUED"));
            if ("COMPLETED".equals(currentStatus)) {
                redis.opsForStream().acknowledge(stream(tenantScope()), group(tenantScope()), record.getId());
                return;
            }
            if ("RETRY_WAIT".equals(currentStatus)) {
                Instant retryAt = parseInstant(current.get("retryAt"));
                // A retry schedule is durable in the ZSET. If the original
                // acknowledgement was lost, discard this old delivery and
                // let releaseDueRetries create the next attempt at its due
                // time instead of executing early.
                if (retryAt != null && retryAt.isAfter(Instant.now())) {
                    redis.opsForStream().acknowledge(stream(tenantScope()), group(tenantScope()), record.getId());
                    return;
                }
            }
            if ("FAILED".equals(currentStatus)) {
                if ("1".equals(String.valueOf(current.getOrDefault("deadLettered", "0")))) {
                    redis.opsForStream().acknowledge(stream(tenantScope()), group(tenantScope()), record.getId());
                } else if (appendDeadLetter(values, ticket, userId, requestJson,
                        parseInt(current.get("attempts")), new IllegalStateException(
                                String.valueOf(current.getOrDefault("error", "购买失败"))))) {
                    redis.opsForHash().put(ticketKey(ticket), "deadLettered", "1");
                    redis.opsForStream().acknowledge(stream(tenantScope()), group(tenantScope()), record.getId());
                }
                return;
            }
            redis.opsForHash().put(ticketKey(ticket), "status", "PROCESSING");
            ReleasePurchaseReqVO request = JsonUtils.parseObject(requestJson, ReleasePurchaseReqVO.class);
            failIfConfigured(ReleaseQueueFailureInjector.Point.BEFORE_PURCHASE);
            ReleasePurchaseRespVO result = service.purchase(userId, request);
            // The local transaction is the source of truth.  Once it returns,
            // a later Redis status write or XACK failure must not be treated
            // as a business failure: the ticket will remain pending and can
            // be safely redelivered because the request key is idempotent.
            purchaseCommitted = true;
            failIfConfigured(ReleaseQueueFailureInjector.Point.AFTER_COMMIT_STATUS);
            redis.opsForHash().put(ticketKey(ticket), "status", "COMPLETED");
            redis.opsForHash().put(ticketKey(ticket), "result", JsonUtils.toJsonString(result));
            recordLatency(current, "completed");
            increment("curmerce.release.distributed.completed");
            failIfConfigured(ReleaseQueueFailureInjector.Point.ACK);
            redis.opsForStream().acknowledge(stream(tenantScope()), group(tenantScope()), record.getId());
        } catch (RuntimeException ex) {
            if (purchaseCommitted) {
                increment("curmerce.release.distributed.post-commit-errors");
                return;
            }
            try {
                Object currentAttempts = redis.opsForHash().get(ticketKey(ticket), "attempts");
                int attempts = Integer.parseInt(currentAttempts == null ? "0" : String.valueOf(currentAttempts)) + 1;
                redis.opsForHash().put(ticketKey(ticket), "attempts", String.valueOf(attempts));
                boolean terminal = attempts >= Math.max(1, maxAttempts) || ex instanceof IllegalArgumentException
                        || ex.getClass().getName().contains("ServiceException");
                if (terminal) {
                    redis.opsForHash().put(ticketKey(ticket), "status", "FAILED");
                    redis.opsForHash().put(ticketKey(ticket), "error", ex.getMessage() == null ? "购买失败" : ex.getMessage());
                    redis.opsForHash().put(ticketKey(ticket), "failedAt", Instant.now().toString());
                    recordLatency(current, "failed");
                    boolean deadLettered = appendDeadLetter(values, ticket, userId, requestJson, attempts, ex);
                    if (deadLettered) redis.opsForHash().put(ticketKey(ticket), "deadLettered", "1");
                    redis.execute(REMOVE_RETRY, List.of(retryZset(tenantScope())), ticket);
                    increment("curmerce.release.distributed.failed");
                    if (deadLettered) redis.opsForStream().acknowledge(stream(tenantScope()), group(tenantScope()), record.getId());
                } else {
                    long delay = retryDelayMs(attempts);
                    long retryAt = System.currentTimeMillis() + delay;
                    redis.execute(SCHEDULE_RETRY, List.of(ticketKey(ticket), retryZset(tenantScope())),
                            String.valueOf(attempts), ex.getMessage() == null ? "购买暂时失败" : ex.getMessage(),
                            Instant.ofEpochMilli(retryAt).toString(), String.valueOf(retryAt), ticket);
                    redis.opsForStream().acknowledge(stream(tenantScope()), group(tenantScope()), record.getId());
                    increment("curmerce.release.distributed.retried");
                }
            } catch (RuntimeException ignored) {
                increment("curmerce.release.distributed.poll-errors");
            }
        } finally {
            try {
                redis.execute(RELEASE_LOCK, List.of(lockKey), consumer);
            } catch (RuntimeException ignored) {
                increment("curmerce.release.distributed.poll-errors");
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        // The Redis consumer is ephemeral; pending records remain in the group
        // and can be claimed by another instance after the idle threshold.
    }

    private ReleasePurchaseRespVO parseResult(Object value) {
        if (value == null) return null;
        try { return JsonUtils.parseObject(String.valueOf(value), ReleasePurchaseRespVO.class); }
        catch (RuntimeException ignored) { return null; }
    }

    private boolean appendDeadLetter(Map<Object, Object> values, String ticket, Long userId,
                                     String requestJson, int attempts, RuntimeException failure) {
        try {
            failIfConfigured(ReleaseQueueFailureInjector.Point.DEAD_LETTER);
            if (ticket != null && !ticket.isBlank() && "1".equals(String.valueOf(
                    redis.opsForHash().get(ticketKey(ticket), "deadLettered")))) return true;
            Map<String, String> dead = new LinkedHashMap<>();
            dead.put("ticket", ticket);
            dead.put("userId", String.valueOf(userId));
            dead.put("request", requestJson);
            dead.put("attempts", String.valueOf(attempts));
            dead.put("error", failure.getMessage() == null ? "购买失败" : failure.getMessage());
            dead.put("failedAt", Instant.now().toString());
            redis.opsForStream().add(deadLetterStream(tenantScope()), dead);
            redis.opsForStream().trim(deadLetterStream(tenantScope()), 10000L);
            return true;
        } catch (RuntimeException ignored) {
            increment("curmerce.release.distributed.dead-letter-errors");
            return false;
        }
    }

    private void recordLatency(Map<Object, Object> ticket, String outcome) {
        if (metrics == null || ticket == null) return;
        Object acceptedAt = ticket.get("acceptedAt");
        if (acceptedAt == null) return;
        try {
            Duration elapsed = Duration.between(Instant.parse(String.valueOf(acceptedAt)), Instant.now());
            if (!elapsed.isNegative()) {
                metrics.timer("curmerce.release.distributed.queue.latency", "outcome", outcome).record(elapsed);
            }
        } catch (RuntimeException ignored) {
            // A malformed timestamp must not affect purchase processing.
        }
    }

    private String ticketKey(String ticket) { return ticketKey(tenantScope(), ticket); }
    private static String ticketKey(String tenant, String ticket) { return TICKET_PREFIX + tenant + ":" + ticket; }
    private static String dedupeKey(String tenant, String ticket) { return DEDUPE_PREFIX + tenant + ":" + ticket; }
    private static String stream(String tenant) { return STREAM + ":" + tenant; }
    private static String deadLetterStream(String tenant) { return DEAD_LETTER_STREAM + ":" + tenant; }
    private static String group(String tenant) { return GROUP + ":" + tenant; }
    private static String retryZset(String tenant) { return RETRY_ZSET + ":" + tenant; }
    private static String processingLock(String tenant, String ticket) { return PROCESS_LOCK_PREFIX + tenant + ":" + ticket; }
    private static String tenantScope() {
        Long value = TenantContextHolder.getTenantId();
        return value == null ? "default" : String.valueOf(value);
    }

    private Set<String> tenantScopes() {
        try {
            Set<String> scopes = redis.opsForSet().members(TENANT_REGISTRY);
            return scopes == null ? Set.of() : Set.copyOf(scopes);
        } catch (RuntimeException ex) {
            return Set.of();
        }
    }

    /** Runs one tenant's Redis stream work with an explicit, leak-proof scope. */
    private void runInTenant(String scope, Runnable work) {
        Long previousTenant = TenantContextHolder.getTenantId();
        boolean previousIgnore = TenantContextHolder.isIgnore();
        try {
            if (scope == null || scope.isBlank() || "default".equals(scope)) {
                TenantContextHolder.clear();
                TenantContextHolder.setIgnore(false);
            } else {
                long value = Long.parseLong(scope);
                if (value < 0) return;
                TenantContextHolder.setTenantId(value);
                TenantContextHolder.setIgnore(false);
            }
            work.run();
        } catch (NumberFormatException ignored) {
            increment("curmerce.release.distributed.invalid-tenant");
        } finally {
            if (previousTenant == null) TenantContextHolder.clear();
            else {
                TenantContextHolder.setTenantId(previousTenant);
                TenantContextHolder.setIgnore(previousIgnore);
            }
        }
    }
    private static void validateTicket(String ticket) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9-]{16,80}")) {
            throw new IllegalArgumentException("排队票据格式无效");
        }
    }
    private long retryDelayMs(int attempts) {
        long base = Math.max(1L, retryBaseDelayMs);
        long max = Math.max(base, retryMaxDelayMs);
        int exponent = Math.max(0, Math.min(attempts - 1, 20));
        long delay;
        try { delay = Math.multiplyExact(base, 1L << exponent); }
        catch (ArithmeticException ex) { delay = max; }
        return Math.min(max, delay);
    }
    private static int parseInt(Object value) {
        try { return value == null ? 0 : Integer.parseInt(String.valueOf(value)); }
        catch (RuntimeException ex) { return 0; }
    }
    private static Instant parseInstant(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return Instant.parse(String.valueOf(value)); }
        catch (RuntimeException ex) { return null; }
    }
    private void increment(String name) { if (metrics != null) metrics.counter(name).increment(); }
    private void failIfConfigured(ReleaseQueueFailureInjector.Point point) {
        if (failureInjector != null) failureInjector.failIfConfigured(point);
    }
    private static DeadLetter toDeadLetter(MapRecord<String, Object, Object> record) {
        Map<Object, Object> values = record.getValue();
        return new DeadLetter(record.getId().getValue(), string(values.get("ticket")),
                parseLong(values.get("userId")), parseInt(values.get("attempts")),
                string(values.get("error")), parseInstant(values.get("failedAt")));
    }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
    private static Long parseLong(Object value) {
        try { return value == null ? null : Long.valueOf(String.valueOf(value)); }
        catch (RuntimeException ex) { return null; }
    }
    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
    public static class QueueUnavailableException extends RuntimeException { public QueueUnavailableException(String message) { super(message); } }
    public record QueueSnapshot(boolean enabled, long streamDepth, long pendingDepth,
                                long retryWaiting, long deadLetterDepth) { }
    public record DeadLetter(String entryId, String ticket, Long userId, int attempts,
                             String error, Instant failedAt) { }
}

package cn.iocoder.yudao.module.commerce.service.release;

import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePurchaseReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePurchaseRespVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Bounded async order queue for flash-sale load leveling. */
@Component
public class ReleasePurchaseQueue {
    private final ReleaseService service;
    private final ThreadPoolExecutor executor;
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<String, Long> owners = new ConcurrentHashMap<>();
    /** Local-mode idempotency prevents duplicate work before the DB transaction. */
    private final Map<String, String> idempotencyTickets = new ConcurrentHashMap<>();
    private final MeterRegistry metrics;

    @Autowired
    public ReleasePurchaseQueue(ReleaseService service,
                                @Value("${curmerce.release.async-queue-capacity:1000}") int capacity,
                                @Value("${curmerce.release.async-worker-threads:4}") int workers,
                                ObjectProvider<MeterRegistry> metricsProvider) {
        this(service, capacity, workers, metricsProvider.getIfAvailable());
    }

    ReleasePurchaseQueue(ReleaseService service, int capacity, int workers, MeterRegistry metrics) {
        this.service = service;
        this.metrics = metrics;
        int safeCapacity = Math.max(10, capacity);
        int safeWorkers = Math.max(1, workers);
        this.executor = new ThreadPoolExecutor(safeWorkers, safeWorkers, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(safeCapacity), new ThreadPoolExecutor.AbortPolicy());
        if (metrics != null) {
            io.micrometer.core.instrument.Gauge.builder("curmerce.release.async.queue.depth", executor,
                            value -> value.getQueue().size()).description("Queued local release purchase tasks")
                    .register(metrics);
        }
    }

    public String enqueue(Long userId, ReleasePurchaseReqVO request) {
        if (userId == null || request == null || request.getItemId() == null
                || request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new IllegalArgumentException("排队购买参数不完整");
        }
        String idempotency = idempotencyKey(userId, request);
        String ticket;
        synchronized (idempotencyTickets) {
            String existing = idempotencyTickets.get(idempotency);
            if (existing != null && tickets.containsKey(existing)) return existing;
            if (existing != null) idempotencyTickets.remove(idempotency, existing);
            ticket = UUID.randomUUID().toString();
            idempotencyTickets.put(idempotency, ticket);
            tickets.put(ticket, new Ticket("QUEUED", null, null, Instant.now()));
            owners.put(ticket, userId);
        }
        try {
            executor.execute(() -> process(ticket, userId, request));
            increment("curmerce.release.async.accepted");
        } catch (RejectedExecutionException ex) {
            tickets.remove(ticket);
            owners.remove(ticket);
            idempotencyTickets.remove(idempotency, ticket);
            increment("curmerce.release.async.rejected");
            throw new QueueFullException("限时发售排队已满，请稍后重试");
        }
        return ticket;
    }

    public Ticket status(String ticket, Long userId) {
        Ticket value = tickets.get(ticket);
        if (value == null) throw new IllegalArgumentException("排队票据不存在或已过期");
        if (userId == null || !userId.equals(owners.get(ticket))) {
            throw new TicketAccessDeniedException("无权查询该排队票据");
        }
        return value;
    }

    @Scheduled(fixedDelayString = "${curmerce.release.async-ticket-cleanup-ms:300000}")
    public void cleanup() {
        Instant cutoff = Instant.now().minusSeconds(3600);
        tickets.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().acceptedAt().isBefore(cutoff);
            if (expired) owners.remove(entry.getKey());
            if (expired) idempotencyTickets.values().removeIf(ticket -> ticket.equals(entry.getKey()));
            return expired;
        });
    }

    private void process(String ticket, Long userId, ReleasePurchaseReqVO request) {
        tickets.computeIfPresent(ticket, (ignored, value) -> value.withStatus("PROCESSING"));
        try {
            ReleasePurchaseRespVO response = service.purchase(userId, request);
            tickets.computeIfPresent(ticket, (ignored, value) -> value.withStatusAndResult("COMPLETED", response));
            increment("curmerce.release.async.completed");
        } catch (RuntimeException ex) {
            tickets.computeIfPresent(ticket, (ignored, value) -> value.withStatusAndError("FAILED", ex.getMessage()));
            increment("curmerce.release.async.failed");
        }
    }

    public int queueDepth() { return executor.getQueue().size(); }

    public void shutdown() { executor.shutdownNow(); }

    private static String idempotencyKey(Long userId, ReleasePurchaseReqVO request) {
        String raw = userId + ":" + request.getItemId() + ":" + request.getIdempotencyKey().trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private void increment(String name) {
        if (metrics != null) metrics.counter(name).increment();
    }

    public record Ticket(String status, ReleasePurchaseRespVO result, String error, Instant acceptedAt,
                         int attempts, Instant retryAt) {
        public Ticket(String status, ReleasePurchaseRespVO result, String error, Instant acceptedAt) {
            this(status, result, error, acceptedAt, 0, null);
        }
        Ticket withStatus(String value) { return new Ticket(value, result, error, acceptedAt, attempts, retryAt); }
        Ticket withStatusAndResult(String value, ReleasePurchaseRespVO response) { return new Ticket(value, response, null, acceptedAt, attempts, null); }
        Ticket withStatusAndError(String value, String message) { return new Ticket(value, null, message, acceptedAt, attempts, null); }
    }
    public static class QueueFullException extends RuntimeException { public QueueFullException(String message) { super(message); } }
    public static class TicketAccessDeniedException extends RuntimeException { public TicketAccessDeniedException(String message) { super(message); } }
}

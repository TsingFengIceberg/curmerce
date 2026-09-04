package cn.iocoder.yudao.module.commerce.service.release;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePurchaseReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePurchaseRespVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleasePurchaseCommandDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleasePurchaseCommandMapper;
import cn.iocoder.yudao.module.commerce.enums.outbox.CommerceOutboxEventTypeEnum;
import cn.iocoder.yudao.module.commerce.service.outbox.CommerceOutboxEventAppender;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Optional Kafka load-leveling boundary for limited-release purchases.
 *
 * <p>Submitting a command writes both this durable row and a transactional
 * commerce Outbox event. Kafka only transports that committed intent. A
 * consumer can crash after the purchase commits because the existing purchase
 * idempotency key makes the retry return the original order; the command is
 * then completed by a later delivery. Redis reservations are still released
 * by {@link ReleaseServiceImpl} on any local transaction failure.</p>
 */
@Component
public class ReleaseKafkaPurchaseQueue {
    static final int QUEUED = 10;
    static final int PROCESSING = 20;
    static final int COMPLETED = 30;
    static final int FAILED = 40;
    static final int RETRY_WAIT = 50;

    private final CommerceReleasePurchaseCommandMapper commandMapper;
    private final CommerceOutboxEventAppender outboxAppender;
    private final ReleaseService releaseService;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry metrics;
    private final boolean configured;
    private final boolean kafkaTransport;
    private final int maxAttempts;
    private final long retryBaseDelayMs;
    private final long retryMaxDelayMs;
    private final long processingLeaseSeconds;

    public ReleaseKafkaPurchaseQueue(CommerceReleasePurchaseCommandMapper commandMapper,
                                     CommerceOutboxEventAppender outboxAppender,
                                     ReleaseService releaseService,
                                     TransactionTemplate transactionTemplate,
                                     ObjectProvider<MeterRegistry> metricsProvider,
                                     @Value("${curmerce.release.kafka-queue-enabled:false}") boolean configured,
                                     @Value("${curmerce.outbox.transport:redis}") String outboxTransport,
                                     @Value("${curmerce.release.kafka-max-attempts:5}") int maxAttempts,
                                     @Value("${curmerce.release.kafka-retry-base-delay-ms:500}") long retryBaseDelayMs,
                                     @Value("${curmerce.release.kafka-retry-max-delay-ms:30000}") long retryMaxDelayMs,
                                     @Value("${curmerce.release.kafka-processing-lease-seconds:120}") long processingLeaseSeconds) {
        this.commandMapper = commandMapper;
        this.outboxAppender = outboxAppender;
        this.releaseService = releaseService;
        this.transactionTemplate = transactionTemplate;
        this.metrics = metricsProvider.getIfAvailable();
        this.configured = configured;
        this.kafkaTransport = "kafka".equalsIgnoreCase(outboxTransport == null ? "" : outboxTransport.trim());
        this.maxAttempts = Math.max(1, Math.min(20, maxAttempts));
        this.retryBaseDelayMs = Math.max(100L, retryBaseDelayMs);
        this.retryMaxDelayMs = Math.max(this.retryBaseDelayMs, retryMaxDelayMs);
        this.processingLeaseSeconds = Math.max(10L, Math.min(3600L, processingLeaseSeconds));
    }

    public boolean enabled() {
        return configured && kafkaTransport;
    }

    public String enqueue(Long userId, ReleasePurchaseReqVO request) {
        requireEnabled();
        validateRequest(userId, request);
        return transactionTemplate.execute(status -> {
            CommerceReleasePurchaseCommandDO existing = commandMapper.selectByBuyerItemAndKey(userId,
                    request.getItemId(), request.getIdempotencyKey().trim());
            if (existing != null) {
                requireEquivalent(existing, request);
                increment("curmerce.release.kafka.commands", "result", "duplicate");
                return existing.getTicket();
            }
            CommerceReleasePurchaseCommandDO command = new CommerceReleasePurchaseCommandDO()
                    .setTicket(UUID.randomUUID().toString()).setBuyerUserId(userId).setItemId(request.getItemId())
                    .setQuantity(request.getQuantity()).setAddressId(request.getAddressId())
                    .setIdempotencyKey(request.getIdempotencyKey().trim()).setStatus(QUEUED)
                    .setAttempts(0).setDispatchVersion(1);
            try {
                commandMapper.insert(command);
            } catch (DuplicateKeyException ex) {
                // A racing retry may have committed immediately before this
                // transaction. The natural unique key is the idempotency
                // boundary, so return that ticket only for an equivalent request.
                CommerceReleasePurchaseCommandDO duplicate = commandMapper.selectByBuyerItemAndKey(userId,
                        request.getItemId(), request.getIdempotencyKey().trim());
                if (duplicate == null) throw ex;
                requireEquivalent(duplicate, request);
                return duplicate.getTicket();
            }
            appendDispatch(command);
            increment("curmerce.release.kafka.commands", "result", "queued");
            return command.getTicket();
        });
    }

    public ReleasePurchaseQueue.Ticket status(String ticket, Long userId) {
        requireEnabled();
        CommerceReleasePurchaseCommandDO command = owned(ticket, userId, false);
        return toTicket(command);
    }

    public boolean retry(String ticket, Long userId) {
        requireEnabled();
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            CommerceReleasePurchaseCommandDO command = owned(ticket, userId, true);
            if (!Integer.valueOf(FAILED).equals(command.getStatus())) return false;
            command.setStatus(QUEUED).setAttempts(0).setLastError(null).setRetryAt(null)
                    .setProcessingToken(null).setProcessingDeadline(null)
                    .setDispatchVersion(nextDispatchVersion(command));
            commandMapper.updateById(command);
            appendDispatch(command);
            increment("curmerce.release.kafka.commands", "result", "operator-retry");
            return true;
        }));
    }

    /** Called by the Kafka handler. Every mutable transition is token-guarded. */
    public void consume(Long commandId) {
        if (!enabled() || commandId == null) return;
        ClaimedCommand claimed = transactionTemplate.execute(status -> claim(commandId));
        if (claimed == null) return;
        try {
            ReleasePurchaseRespVO result = releaseService.purchase(claimed.command().getBuyerUserId(), request(claimed.command()));
            complete(claimed, result);
            increment("curmerce.release.kafka.commands", "result", "completed");
        } catch (ServiceException ex) {
            if (retriable(ex) && claimed.command().getAttempts() < maxAttempts) {
                retryLater(claimed, ex);
            } else {
                fail(claimed, ex);
            }
        } catch (RuntimeException ex) {
            if (claimed.command().getAttempts() < maxAttempts) retryLater(claimed, ex);
            else fail(claimed, ex);
        }
    }

    /** Re-dispatches abandoned leases and due transient failures through the same Outbox contract. */
    @Scheduled(fixedDelayString = "${curmerce.release.kafka-recovery-delay-ms:5000}")
    public void recoverDueCommands() {
        if (!enabled()) return;
        List<CommerceReleasePurchaseCommandDO> candidates = commandMapper.selectRecoverable(LocalDateTime.now(), 100);
        for (CommerceReleasePurchaseCommandDO candidate : candidates) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    CommerceReleasePurchaseCommandDO command = commandMapper.selectByIdForUpdate(candidate.getId());
                    if (command == null || !recoverable(command, LocalDateTime.now())) return;
                    command.setStatus(QUEUED).setProcessingToken(null).setProcessingDeadline(null).setRetryAt(null)
                            .setDispatchVersion(nextDispatchVersion(command));
                    commandMapper.updateById(command);
                    appendDispatch(command);
                    increment("curmerce.release.kafka.commands", "result", "recovered");
                });
            } catch (RuntimeException ex) {
                increment("curmerce.release.kafka.commands", "result", "recovery-error");
            }
        }
    }

    public Snapshot snapshot() {
        if (!enabled()) return new Snapshot(false, 0L, 0L, 0L, 0L);
        try {
            long queued = commandMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CommerceReleasePurchaseCommandDO>()
                    .eq(CommerceReleasePurchaseCommandDO::getStatus, QUEUED));
            long processing = commandMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CommerceReleasePurchaseCommandDO>()
                    .eq(CommerceReleasePurchaseCommandDO::getStatus, PROCESSING));
            long retry = commandMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CommerceReleasePurchaseCommandDO>()
                    .eq(CommerceReleasePurchaseCommandDO::getStatus, RETRY_WAIT));
            long failed = commandMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CommerceReleasePurchaseCommandDO>()
                    .eq(CommerceReleasePurchaseCommandDO::getStatus, FAILED));
            return new Snapshot(true, queued, processing, retry, failed);
        } catch (RuntimeException ex) {
            return new Snapshot(true, -1L, -1L, -1L, -1L);
        }
    }

    private ClaimedCommand claim(Long id) {
        CommerceReleasePurchaseCommandDO command = commandMapper.selectByIdForUpdate(id);
        if (command == null || !Integer.valueOf(QUEUED).equals(command.getStatus())) return null;
        String token = UUID.randomUUID().toString();
        command.setStatus(PROCESSING).setAttempts((command.getAttempts() == null ? 0 : command.getAttempts()) + 1)
                .setProcessingToken(token).setProcessingDeadline(LocalDateTime.now().plusSeconds(processingLeaseSeconds))
                .setRetryAt(null);
        commandMapper.updateById(command);
        return new ClaimedCommand(command, token);
    }

    private void complete(ClaimedCommand claimed, ReleasePurchaseRespVO response) {
        transactionTemplate.executeWithoutResult(status -> {
            CommerceReleasePurchaseCommandDO command = commandMapper.selectByIdForUpdate(claimed.command().getId());
            if (!ownsLease(command, claimed.token())) return;
            command.setStatus(COMPLETED).setResult(JsonUtils.toJsonString(response)).setLastError(null).setRetryAt(null)
                    .setProcessingToken(null).setProcessingDeadline(null);
            commandMapper.updateById(command);
        });
    }

    private void retryLater(ClaimedCommand claimed, RuntimeException failure) {
        transactionTemplate.executeWithoutResult(status -> {
            CommerceReleasePurchaseCommandDO command = commandMapper.selectByIdForUpdate(claimed.command().getId());
            if (!ownsLease(command, claimed.token())) return;
            int attempts = command.getAttempts() == null ? 1 : command.getAttempts();
            command.setStatus(RETRY_WAIT).setLastError(message(failure))
                    .setRetryAt(LocalDateTime.now().plusNanos(retryDelayMillis(attempts) * 1_000_000L))
                    .setProcessingToken(null).setProcessingDeadline(null);
            commandMapper.updateById(command);
            increment("curmerce.release.kafka.commands", "result", "retry-wait");
        });
    }

    private void fail(ClaimedCommand claimed, RuntimeException failure) {
        transactionTemplate.executeWithoutResult(status -> {
            CommerceReleasePurchaseCommandDO command = commandMapper.selectByIdForUpdate(claimed.command().getId());
            if (!ownsLease(command, claimed.token())) return;
            command.setStatus(FAILED).setLastError(message(failure)).setRetryAt(null)
                    .setProcessingToken(null).setProcessingDeadline(null);
            commandMapper.updateById(command);
            increment("curmerce.release.kafka.commands", "result", "failed");
        });
    }

    private void appendDispatch(CommerceReleasePurchaseCommandDO command) {
        outboxAppender.appendState(CommerceOutboxEventTypeEnum.RELEASE_PURCHASE_COMMAND, command.getId(), Map.of(
                "commandId", command.getId(), "ticket", command.getTicket(),
                "dispatchVersion", command.getDispatchVersion()));
    }

    private CommerceReleasePurchaseCommandDO owned(String ticket, Long userId, boolean forUpdate) {
        if (ticket == null || !ticket.matches("[A-Za-z0-9-]{16,80}")) throw new IllegalArgumentException("排队票据格式无效");
        CommerceReleasePurchaseCommandDO command = forUpdate ? commandMapper.selectByTicketForUpdate(ticket)
                : commandMapper.selectByTicket(ticket);
        if (command == null) throw new IllegalArgumentException("排队票据不存在或已过期");
        if (userId == null || !userId.equals(command.getBuyerUserId())) {
            throw new ReleasePurchaseQueue.TicketAccessDeniedException("无权查询该排队票据");
        }
        return command;
    }

    private static ReleasePurchaseReqVO request(CommerceReleasePurchaseCommandDO command) {
        return new ReleasePurchaseReqVO().setItemId(command.getItemId()).setQuantity(command.getQuantity())
                .setAddressId(command.getAddressId()).setIdempotencyKey(command.getIdempotencyKey());
    }

    private static ReleasePurchaseQueue.Ticket toTicket(CommerceReleasePurchaseCommandDO command) {
        ReleasePurchaseRespVO result = null;
        try { if (command.getResult() != null) result = JsonUtils.parseObject(command.getResult(), ReleasePurchaseRespVO.class); }
        catch (RuntimeException ignored) { }
        return new ReleasePurchaseQueue.Ticket(statusName(command.getStatus()), result, command.getLastError(),
                command.getCreateTime() == null ? Instant.EPOCH : command.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant(),
                command.getAttempts() == null ? 0 : command.getAttempts(),
                command.getRetryAt() == null ? null : command.getRetryAt().atZone(java.time.ZoneId.systemDefault()).toInstant());
    }

    private static String statusName(Integer value) {
        if (value == null) return "QUEUED";
        return switch (value) { case PROCESSING -> "PROCESSING"; case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED"; case RETRY_WAIT -> "RETRY_WAIT"; default -> "QUEUED"; };
    }

    private static boolean ownsLease(CommerceReleasePurchaseCommandDO command, String token) {
        return command != null && Integer.valueOf(PROCESSING).equals(command.getStatus())
                && token.equals(command.getProcessingToken());
    }

    private static boolean recoverable(CommerceReleasePurchaseCommandDO command, LocalDateTime now) {
        return (Integer.valueOf(RETRY_WAIT).equals(command.getStatus()) && command.getRetryAt() != null && !command.getRetryAt().isAfter(now))
                || (Integer.valueOf(PROCESSING).equals(command.getStatus()) && command.getProcessingDeadline() != null
                && !command.getProcessingDeadline().isAfter(now));
    }

    private static void validateRequest(Long userId, ReleasePurchaseReqVO request) {
        if (userId == null || request == null || request.getItemId() == null || request.getQuantity() == null
                || request.getQuantity() < 1 || request.getAddressId() == null || request.getIdempotencyKey() == null
                || request.getIdempotencyKey().isBlank() || request.getIdempotencyKey().trim().length() > 64) {
            throw new IllegalArgumentException("限时发售异步购买参数不完整");
        }
    }

    private static void requireEquivalent(CommerceReleasePurchaseCommandDO command, ReleasePurchaseReqVO request) {
        if (!command.getQuantity().equals(request.getQuantity()) || !command.getAddressId().equals(request.getAddressId())) {
            throw new IllegalArgumentException("限时发售幂等键已用于不同购买参数");
        }
    }

    private int nextDispatchVersion(CommerceReleasePurchaseCommandDO command) {
        return Math.addExact(command.getDispatchVersion() == null ? 0 : command.getDispatchVersion(), 1);
    }

    private boolean retriable(ServiceException exception) {
        return exception.getCode() != null && exception.getCode().equals(
                cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.RELEASE_RESERVATION_UNAVAILABLE.getCode());
    }

    private long retryDelayMillis(int attempts) {
        long multiplier = 1L << Math.min(20, Math.max(0, attempts - 1));
        try { return Math.min(retryMaxDelayMs, Math.multiplyExact(retryBaseDelayMs, multiplier)); }
        catch (ArithmeticException ex) { return retryMaxDelayMs; }
    }

    private static String message(RuntimeException failure) {
        String value = failure.getMessage();
        return value == null || value.isBlank() ? failure.getClass().getSimpleName() : value.substring(0, Math.min(500, value.length()));
    }

    private void requireEnabled() {
        if (!enabled()) throw new IllegalStateException("Kafka 限时发售队列未启用");
    }

    private void increment(String name, String key, String value) {
        if (metrics != null) metrics.counter(name, key, value).increment();
    }

    private record ClaimedCommand(CommerceReleasePurchaseCommandDO command, String token) { }
    public record Snapshot(boolean enabled, long queued, long processing, long retryWaiting, long failed) { }
}

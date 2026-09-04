package cn.iocoder.yudao.module.commerce.service.outbox.kafka;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Durable idempotent ledger consumer. Typed projections run before a receipt
 * is marked processed; failures remain replayable and are also sent to the
 * Kafka DLT by the listener error handler.
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "curmerce.outbox", name = "transport", havingValue = "kafka")
public class CommerceKafkaEventConsumer {

    private final CommerceKafkaReceiptService receiptService;
    private final CommerceKafkaEventDispatcher dispatcher;
    private final MeterRegistry meterRegistry;
    @Value("${curmerce.outbox.kafka.consumer-group:curmerce-core-ledger-v1}") private String consumerGroup;

    public CommerceKafkaEventConsumer(CommerceKafkaReceiptService receiptService,
                                      CommerceKafkaEventDispatcher dispatcher,
                                      MeterRegistry meterRegistry) {
        this.receiptService = receiptService;
        this.dispatcher = dispatcher;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(containerFactory = "commerceKafkaListenerContainerFactory",
            topics = "${curmerce.outbox.kafka.topic:curmerce.events.v1}",
            groupId = "${curmerce.outbox.kafka.consumer-group:curmerce-core-ledger-v1}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        CommerceKafkaEventMessage message = JsonUtils.parseObject(record.value(), CommerceKafkaEventMessage.class);
        Long tenantId = parseTenantId(message == null ? null : message.getTenantId());
        if (message == null || message.getEventId() == null || message.getEventType() == null || tenantId == null) {
            throw new IllegalArgumentException("Kafka commerce event envelope is invalid");
        }
        Long previousTenant = TenantContextHolder.getTenantId();
        boolean previousIgnore = TenantContextHolder.isIgnore();
        TenantContextHolder.setTenantId(tenantId);
        TenantContextHolder.setIgnore(false);
        try {
            var begin = receiptService.begin(message);
            var receipt = begin.receipt();
            if (!begin.claimed()) {
                acknowledgment.acknowledge();
                meterRegistry.counter("curmerce.kafka.consumer.events", "result", "duplicate").increment();
                return;
            }
            try {
                int handled = dispatcher.dispatch(message);
                receiptService.processed(receipt.getId());
                acknowledgment.acknowledge();
                meterRegistry.counter("curmerce.kafka.consumer.events", "result", "accepted").increment();
                log.info("processed commerce Kafka event eventId={}, eventType={}, key={}, tenantId={}, handlers={}",
                        message.getEventId(), message.getEventType(), message.getEventKey(), tenantId, handled);
            } catch (RuntimeException ex) {
                receiptService.failed(receipt.getId(), receipt.getAttempts() == null ? 1 : receipt.getAttempts(), ex);
                throw ex;
            }
        } finally {
            if (previousTenant == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.setTenantId(previousTenant);
                TenantContextHolder.setIgnore(previousIgnore);
            }
        }
    }

    /** Consumer threads do not pass through the web tenant filter; fail closed
     * for missing or non-numeric envelopes instead of selecting a default tenant. */
    static Long parseTenantId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            long value = Long.parseLong(raw.trim());
            return value >= 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

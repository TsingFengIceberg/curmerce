package cn.iocoder.yudao.module.commerce.service.outbox.kafka;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
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
        if (message == null || message.getEventId() == null || message.getEventType() == null) {
            throw new IllegalArgumentException("Kafka commerce event envelope is invalid");
        }
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
            log.info("processed commerce Kafka event eventId={}, eventType={}, key={}, handlers={}",
                    message.getEventId(), message.getEventType(), message.getEventKey(), handled);
        } catch (RuntimeException ex) {
            receiptService.failed(receipt.getId(), receipt.getAttempts() == null ? 1 : receipt.getAttempts(), ex);
            throw ex;
        }
    }
}

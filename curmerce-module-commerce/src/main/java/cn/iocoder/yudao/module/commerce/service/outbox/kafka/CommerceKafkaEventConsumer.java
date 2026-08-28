package cn.iocoder.yudao.module.commerce.service.outbox.kafka;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.commerce.dal.dataobject.outbox.CommerceKafkaConsumerReceiptDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.outbox.CommerceKafkaConsumerReceiptMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Idempotent ledger consumer. Domain projections can subscribe to the same
 * topic later; the unique receipt is the first durable duplicate guard.
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "curmerce.outbox", name = "transport", havingValue = "kafka")
public class CommerceKafkaEventConsumer {

    @Resource private CommerceKafkaConsumerReceiptMapper receiptMapper;
    @Resource private MeterRegistry meterRegistry;
    @Value("${curmerce.outbox.kafka.consumer-group:curmerce-core-ledger-v1}") private String consumerGroup;

    @Transactional(rollbackFor = Exception.class)
    @KafkaListener(containerFactory = "commerceKafkaListenerContainerFactory",
            topics = "${curmerce.outbox.kafka.topic:curmerce.events.v1}",
            groupId = "${curmerce.outbox.kafka.consumer-group:curmerce-core-ledger-v1}")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        CommerceKafkaEventMessage message = JsonUtils.parseObject(record.value(), CommerceKafkaEventMessage.class);
        if (message == null || message.getEventId() == null || message.getEventType() == null) {
            throw new IllegalArgumentException("Kafka commerce event envelope is invalid");
        }
        if (receiptMapper.exists(consumerGroup, message.getEventId())) {
            acknowledgment.acknowledge();
            meterRegistry.counter("curmerce.kafka.consumer.events", "result", "duplicate").increment();
            return;
        }
        try {
            receiptMapper.insert(new CommerceKafkaConsumerReceiptDO()
                    .setConsumerGroup(consumerGroup).setEventId(message.getEventId())
                    .setEventType(message.getEventType()).setEventKey(message.getEventKey())
                    .setReceivedTime(LocalDateTime.now().withNano(0)));
        } catch (DuplicateKeyException ignored) {
            acknowledgment.acknowledge();
            meterRegistry.counter("curmerce.kafka.consumer.events", "result", "duplicate").increment();
            return;
        }
        acknowledgment.acknowledge();
        meterRegistry.counter("curmerce.kafka.consumer.events", "result", "accepted").increment();
        log.info("accepted commerce Kafka event eventId={}, eventType={}, key={}",
                message.getEventId(), message.getEventType(), message.getEventKey());
    }
}

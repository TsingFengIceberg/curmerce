package cn.iocoder.yudao.module.commerce.service.outbox.kafka;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.commerce.service.outbox.CommerceOutboxMessagePublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Kafka transport for the existing transactional commerce Outbox. */
@Component
@ConditionalOnProperty(prefix = "curmerce.outbox", name = "transport", havingValue = "kafka")
public class CommerceKafkaEventPublisher implements CommerceOutboxMessagePublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final Duration publishTimeout;

    public CommerceKafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                       @Value("${curmerce.outbox.kafka.topic:curmerce.events.v1}") String topic,
                                       @Value("${curmerce.outbox.kafka.publish-timeout:5s}") Duration publishTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.publishTimeout = publishTimeout;
    }

    @Override
    public void publish(CommerceOutboxEventDO event) {
        CommerceKafkaEventMessage message = new CommerceKafkaEventMessage()
                .setEventId(event.getId()).setEventType(event.getEventType())
                .setEventKey(event.getEventKey()).setAggregateType(event.getAggregateType())
                .setAggregateId(event.getAggregateId()).setPayload(event.getPayload());
        try {
            kafkaTemplate.send(topic, event.getEventKey(), JsonUtils.toJsonString(message))
                    .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publish interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Kafka publish failed", ex);
        }
    }
}

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
    private final String agentProjectionTopic;
    private final Duration publishTimeout;

    public CommerceKafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                       @Value("${curmerce.outbox.kafka.topic:curmerce.events.v1}") String topic,
                                       @Value("${curmerce.outbox.kafka.agent-projection-topic:curmerce.agent.events.v1}") String agentProjectionTopic,
                                       @Value("${curmerce.outbox.kafka.publish-timeout:5s}") Duration publishTimeout) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.agentProjectionTopic = agentProjectionTopic;
        this.publishTimeout = publishTimeout;
    }

    @Override
    public void publish(CommerceOutboxEventDO event) {
        CommerceKafkaEventMessage message = new CommerceKafkaEventMessage()
                .setEventId(event.getId()).setTenantId(event.getTenantId() == null || event.getTenantId().isBlank() ? "default" : event.getTenantId())
                .setEventType(event.getEventType())
                .setEventKey(event.getEventKey()).setAggregateType(event.getAggregateType())
                .setAggregateId(event.getAggregateId()).setPayload(event.getPayload());
        try {
            String body = JsonUtils.toJsonString(message);
            publishTo(topic, message.getTenantId(), event.getAggregateId(), body);
            // Agent consumes only product knowledge changes on its own topic.
            // Publishing both records before returning preserves the Outbox
            // retry contract if the Agent projection broker path is unavailable.
            if ("PRODUCT_CHANGED".equals(event.getEventType()) && agentTopicEnabled()) {
                publishTo(agentProjectionTopic, message.getTenantId(), event.getAggregateId(), body);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publish interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Kafka publish failed", ex);
        }
    }

    private void publishTo(String destination, String tenantId, Long aggregateId, String body) throws Exception {
        kafkaTemplate.send(destination, tenantId + ":" + aggregateId, body)
                .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private boolean agentTopicEnabled() {
        return agentProjectionTopic != null && !agentProjectionTopic.isBlank() && !agentProjectionTopic.equals(topic);
    }
}

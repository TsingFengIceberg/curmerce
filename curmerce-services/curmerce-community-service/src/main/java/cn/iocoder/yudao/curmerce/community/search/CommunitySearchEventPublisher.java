package cn.iocoder.yudao.curmerce.community.search;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.community.dal.dataobject.outbox.CommunitySearchOutboxDO;
import cn.iocoder.yudao.module.community.dal.mysql.outbox.CommunitySearchOutboxMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "curmerce.search", name = "events-enabled", havingValue = "true")
public class CommunitySearchEventPublisher {
    private final CommunitySearchOutboxMapper outboxMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final String agentProjectionTopic;
    private final int maxAttempts;

    public CommunitySearchEventPublisher(CommunitySearchOutboxMapper outboxMapper,
                                         KafkaTemplate<String, String> kafkaTemplate,
                                         @Value("${curmerce.search.kafka-topic:curmerce.events.v1}") String topic,
                                         @Value("${curmerce.agent-projection.kafka-topic:curmerce.agent.events.v1}") String agentProjectionTopic,
                                         @Value("${curmerce.search.max-attempts:5}") int maxAttempts) {
        this.outboxMapper = outboxMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.agentProjectionTopic = agentProjectionTopic;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Scheduled(fixedDelayString = "${curmerce.search.publish-delay-ms:2000}")
    @Transactional(rollbackFor = Exception.class)
    public void publishAvailable() {
        for (CommunitySearchOutboxDO event : outboxMapper.selectPending(50)) {
            try {
                Map<String, Object> envelope = new LinkedHashMap<>();
                envelope.put("eventId", event.getId());
                envelope.put("eventType", event.getEventType());
                envelope.put("eventKey", event.getEventKey());
                envelope.put("aggregateType", event.getAggregateType());
                envelope.put("aggregateId", event.getAggregateId());
                envelope.put("payload", event.getPayload());
                String body = JsonUtils.toJsonString(envelope);
                publishTo(topic, event.getAggregateId(), body);
                if ("POST_CHANGED".equals(event.getEventType()) && agentTopicEnabled()) {
                    publishTo(agentProjectionTopic, event.getAggregateId(), body);
                }
                outboxMapper.markPublished(event.getId(), LocalDateTime.now().withNano(0));
            } catch (Exception ex) {
                int attempts = (event.getAttempts() == null ? 0 : event.getAttempts()) + 1;
                String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                reason = reason.length() > 500 ? reason.substring(0, 500) : reason;
                if (attempts >= maxAttempts) {
                    outboxMapper.markDead(event.getId(), attempts, reason);
                } else {
                    long delay = Math.min(300, 1L << Math.min(attempts, 8));
                    outboxMapper.markRetry(event.getId(), attempts,
                            LocalDateTime.now().plusSeconds(delay).withNano(0), reason);
                }
            }
        }
    }

    private void publishTo(String destination, Long aggregateId, String body) throws Exception {
        kafkaTemplate.send(destination, String.valueOf(aggregateId), body)
                .get(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    private boolean agentTopicEnabled() {
        return agentProjectionTopic != null && !agentProjectionTopic.isBlank() && !agentProjectionTopic.equals(topic);
    }
}

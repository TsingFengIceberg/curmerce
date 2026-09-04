package cn.iocoder.yudao.module.community.service.search;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.community.dal.dataobject.outbox.CommunitySearchOutboxDO;
import cn.iocoder.yudao.module.community.dal.mysql.outbox.CommunitySearchOutboxMapper;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Publishes committed post-state Outbox rows to the shared projection topic. */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "curmerce.community-search-outbox", name = "transport", havingValue = "kafka")
public class CommunitySearchKafkaOutboxPublisher {
    private static final int MAX_ATTEMPTS = 5;
    private static final long BASE_RETRY_SECONDS = 30L;

    @Resource private CommunitySearchOutboxMapper outboxMapper;
    @Resource private KafkaTemplate<String, String> communitySearchKafkaTemplate;
    @Resource private MeterRegistry meterRegistry;
    @Value("${curmerce.community-search-outbox.kafka.topic:curmerce.events.v1}") private String topic;
    @Value("${curmerce.community-search-outbox.kafka.agent-projection-topic:curmerce.agent.events.v1}") private String agentProjectionTopic;
    @Value("${curmerce.community-search-outbox.kafka.publish-timeout:5s}") private Duration publishTimeout;

    @Scheduled(fixedDelayString = "${curmerce.community-search-outbox.publish-delay-ms:5000}")
    @Transactional(rollbackFor = Exception.class)
    public void publishPending() {
        List<CommunitySearchOutboxDO> events = outboxMapper.selectPending(100);
        for (CommunitySearchOutboxDO event : events) {
            try {
                Map<String, Object> envelope = new HashMap<>();
                envelope.put("eventId", event.getId());
                String tenantId = event.getTenantId() == null || event.getTenantId().isBlank() ? "default" : event.getTenantId();
                envelope.put("tenantId", tenantId);
                envelope.put("eventType", event.getEventType());
                envelope.put("eventKey", event.getEventKey());
                envelope.put("aggregateType", event.getAggregateType());
                envelope.put("aggregateId", event.getAggregateId());
                envelope.put("payload", event.getPayload());
                String body = JsonUtils.toJsonString(envelope);
                publishTo(topic, tenantId, event.getAggregateId(), body);
                // The Agent has a dedicated topic so unrelated commerce
                // events cannot poison its knowledge-projection partitions.
                if ("POST_CHANGED".equals(event.getEventType()) && agentTopicEnabled()) {
                    publishTo(agentProjectionTopic, tenantId, event.getAggregateId(), body);
                }
                if (outboxMapper.markPublished(event.getId(), LocalDateTime.now().withNano(0)) == 1) {
                    meterRegistry.counter("curmerce.community.search.outbox.published", "result", "success").increment();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                retry(event, ex);
                return;
            } catch (Exception ex) {
                retry(event, ex);
            }
        }
    }

    private void publishTo(String destination, String tenantId, Long aggregateId, String body) throws Exception {
        communitySearchKafkaTemplate.send(destination, tenantId + ":" + aggregateId, body)
                .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private boolean agentTopicEnabled() {
        return agentProjectionTopic != null && !agentProjectionTopic.isBlank() && !agentProjectionTopic.equals(topic);
    }

    private void retry(CommunitySearchOutboxDO event, Exception ex) {
        int attempts = (event.getAttempts() == null ? 0 : event.getAttempts()) + 1;
        String error = StrUtil.maxLength(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), 500);
        if (attempts >= MAX_ATTEMPTS) {
            outboxMapper.markDead(event.getId(), attempts, error);
            meterRegistry.counter("curmerce.community.search.outbox.published", "result", "dead").increment();
        } else {
            long delay = BASE_RETRY_SECONDS * (1L << (attempts - 1));
            outboxMapper.markRetry(event.getId(), attempts, LocalDateTime.now().plusSeconds(delay).withNano(0), error);
            meterRegistry.counter("curmerce.community.search.outbox.published", "result", "retry").increment();
        }
        log.warn("community search Outbox publish failed: id={}, attempts={}, reason={}", event.getId(), attempts, error);
    }
}

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "curmerce.community-search-outbox", name = "transport", havingValue = "kafka")
class CommunitySearchKafkaOutboxConfiguration {
    @Bean
    ProducerFactory<String, String> communitySearchKafkaProducerFactory(
            @Value("${curmerce.community-search-outbox.kafka.bootstrap-servers:127.0.0.1:19092}") String bootstrapServers) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    KafkaTemplate<String, String> communitySearchKafkaTemplate(ProducerFactory<String, String> communitySearchKafkaProducerFactory) {
        return new KafkaTemplate<>(communitySearchKafkaProducerFactory);
    }
}

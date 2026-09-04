package cn.iocoder.yudao.curmerce.agent;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Kafka consumer that deliberately acknowledges only completed projections. */
@Component
@ConditionalOnProperty(prefix = "curmerce.agent.event-projection", name = "enabled", havingValue = "true")
public class AgentKnowledgeKafkaEventConsumer {
    private final AgentKnowledgeProjectionService projectionService;
    private final MeterRegistry meterRegistry;

    public AgentKnowledgeKafkaEventConsumer(AgentKnowledgeProjectionService projectionService, MeterRegistry meterRegistry) {
        this.projectionService = projectionService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topics = "${curmerce.agent.event-projection.kafka-topic:curmerce.agent.events.v1}",
            groupId = "${curmerce.agent.event-projection.kafka-consumer-group:curmerce-agent-knowledge-v1}",
            containerFactory = "agentKnowledgeKafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        Map<String, Object> envelope;
        try {
            envelope = JsonUtils.parseMap(record.value());
        } catch (RuntimeException ex) {
            meterRegistry.counter("curmerce.agent.knowledge.kafka.events", "result", "invalid").increment();
            // Let the container's DeadLetterPublishingRecoverer persist the
            // poison record. A manual ACK here would silently discard the
            // evidence and make operator replay impossible.
            throw new AgentKnowledgePoisonMessageException("知识事件不是有效 JSON", ex);
        }
        if (envelope == null) {
            // JsonUtils.parseMap intentionally has a quiet/null contract. A
            // null result is still a malformed Kafka record and must follow
            // the same DLT path as a parser exception.
            meterRegistry.counter("curmerce.agent.knowledge.kafka.events", "result", "invalid").increment();
            throw new AgentKnowledgePoisonMessageException("知识事件不是有效 JSON");
        }
        String type = envelope == null ? "" : String.valueOf(envelope.getOrDefault("eventType", ""));
        if (!"PRODUCT_CHANGED".equals(type) && !"POST_CHANGED".equals(type)) {
            acknowledgment.acknowledge();
            meterRegistry.counter("curmerce.agent.knowledge.kafka.events", "result", "ignored").increment();
            return;
        }
        String tenant = envelope == null ? "" : String.valueOf(envelope.getOrDefault("tenantId", ""));
        if (tenant.isBlank()) {
            // Knowledge events must carry their tenant explicitly. Accepting a
            // missing tenant would project a shared-topic record into the
            // consumer's default namespace and can leak data across tenants.
            meterRegistry.counter("curmerce.agent.knowledge.kafka.events", "result", "missing-tenant").increment();
            throw new AgentKnowledgePoisonMessageException("知识事件缺少租户标识");
        }
        try {
            try (AgentRequestContext.Scope ignored = AgentRequestContext.open("kafka-consumer", tenant)) {
                projectionService.project(envelope);
            }
            acknowledgment.acknowledge();
            meterRegistry.counter("curmerce.agent.knowledge.kafka.events", "result", "accepted").increment();
        } catch (IllegalArgumentException ex) {
            // Invalid aggregate identity/payload is routed to Kafka DLT by
            // the container error handler instead of being discarded.
            meterRegistry.counter("curmerce.agent.knowledge.kafka.events", "result", "invalid").increment();
            throw new AgentKnowledgePoisonMessageException("知识事件负载无效", ex);
        }
    }

    /** Permanent poison event; the Kafka container routes it to the DLT. */
    static final class AgentKnowledgePoisonMessageException extends RuntimeException {
        AgentKnowledgePoisonMessageException(String message) { super(message); }
        AgentKnowledgePoisonMessageException(String message, Throwable cause) { super(message, cause); }
    }
}

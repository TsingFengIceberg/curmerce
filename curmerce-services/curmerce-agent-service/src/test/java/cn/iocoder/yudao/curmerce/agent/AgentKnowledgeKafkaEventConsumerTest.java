package cn.iocoder.yudao.curmerce.agent;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentKnowledgeKafkaEventConsumerTest {

    @Test
    void acknowledgesOnlyAfterProjectionCompletes() {
        AgentKnowledgeProjectionService projection = mock(AgentKnowledgeProjectionService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        AgentKnowledgeKafkaEventConsumer consumer = new AgentKnowledgeKafkaEventConsumer(projection, new SimpleMeterRegistry());
        ConsumerRecord<String, String> record = record();

        consumer.consume(record, acknowledgment);

        verify(projection).project(Map.of("eventId", 7, "eventType", "POST_CHANGED",
                "tenantId", "tenant-a", "payload", Map.of("postId", 3)));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void doesNotAcknowledgeWhenProjectionFails() {
        AgentKnowledgeProjectionService projection = mock(AgentKnowledgeProjectionService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        when(projection.project(any())).thenThrow(new IllegalStateException("projection unavailable"));
        AgentKnowledgeKafkaEventConsumer consumer = new AgentKnowledgeKafkaEventConsumer(projection, new SimpleMeterRegistry());

        assertThatThrownBy(() -> consumer.consume(record(), acknowledgment))
                .isInstanceOf(IllegalStateException.class).hasMessage("projection unavailable");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void acknowledgesWellFormedNonKnowledgeEventsWithoutInvokingProjection() {
        AgentKnowledgeProjectionService projection = mock(AgentKnowledgeProjectionService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        AgentKnowledgeKafkaEventConsumer consumer = new AgentKnowledgeKafkaEventConsumer(projection, new SimpleMeterRegistry());

        consumer.consume(new ConsumerRecord<>("curmerce.agent.events.v1", 0, 2L, "order:9",
                "{\"eventId\":9,\"eventType\":\"ORDER_PAID\",\"payload\":{\"orderId\":9}}"), acknowledgment);

        verify(projection, never()).project(any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void routesMalformedRecordsToTheContainerDeadLetterHandler() {
        AgentKnowledgeProjectionService projection = mock(AgentKnowledgeProjectionService.class);
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        AgentKnowledgeKafkaEventConsumer consumer = new AgentKnowledgeKafkaEventConsumer(projection, new SimpleMeterRegistry());

        assertThatThrownBy(() -> consumer.consume(new ConsumerRecord<>("curmerce.agent.events.v1", 0, 3L, "bad", "not-json"), acknowledgment))
                .isInstanceOf(AgentKnowledgeKafkaEventConsumer.AgentKnowledgePoisonMessageException.class);

        verify(projection, never()).project(any());
        verify(acknowledgment, never()).acknowledge();
    }

    private static ConsumerRecord<String, String> record() {
        return new ConsumerRecord<>("curmerce.events.v1", 0, 1L, "post:3",
                "{\"eventId\":7,\"eventType\":\"POST_CHANGED\",\"tenantId\":\"tenant-a\",\"payload\":{\"postId\":3}}");
    }
}

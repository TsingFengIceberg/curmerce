package cn.iocoder.yudao.module.community.service.search;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.community.dal.dataobject.outbox.CommunitySearchOutboxDO;
import cn.iocoder.yudao.module.community.dal.mysql.outbox.CommunitySearchOutboxMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunitySearchKafkaOutboxPublisherTest {
    @Mock private CommunitySearchOutboxMapper outboxMapper;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    private CommunitySearchKafkaOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new CommunitySearchKafkaOutboxPublisher();
        ReflectionTestUtils.setField(publisher, "outboxMapper", outboxMapper);
        ReflectionTestUtils.setField(publisher, "communitySearchKafkaTemplate", kafkaTemplate);
        ReflectionTestUtils.setField(publisher, "meterRegistry", new SimpleMeterRegistry());
        ReflectionTestUtils.setField(publisher, "topic", "curmerce.events.v1");
        ReflectionTestUtils.setField(publisher, "publishTimeout", Duration.ofSeconds(1));
    }

    @Test
    void publishesTypedEnvelopeAndMarksSuccessfulDelivery() {
        CommunitySearchOutboxDO event = event(0);
        when(outboxMapper.selectPending(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(successfulSend());
        when(outboxMapper.markPublished(eq(17L), any(LocalDateTime.class))).thenReturn(1);

        publisher.publishPending();

        ArgumentCaptor<String> envelope = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("curmerce.events.v1"), eq("default:42"), envelope.capture());
        Map<String, Object> body = JsonUtils.parseMap(envelope.getValue());
        assertThat(body).containsEntry("eventId", 17).containsEntry("eventType", "POST_CHANGED")
                .containsEntry("tenantId", "default").containsEntry("aggregateId", 42)
                .containsEntry("payload", "{\"postId\":42}");
        verify(outboxMapper).markPublished(eq(17L), any(LocalDateTime.class));
    }

    @Test
    void firstPublishFailureSchedulesThirtySecondRetry() {
        CommunitySearchOutboxDO event = event(0);
        when(outboxMapper.selectPending(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failedSend());

        publisher.publishPending();

        ArgumentCaptor<LocalDateTime> retryAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxMapper).markRetry(eq(17L), eq(1), retryAt.capture(), anyString());
        assertThat(retryAt.getValue()).isAfter(LocalDateTime.now().plusSeconds(25));
    }

    @Test
    void fifthFailureMovesEventToDeadLetterState() {
        CommunitySearchOutboxDO event = event(4);
        when(outboxMapper.selectPending(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failedSend());

        publisher.publishPending();

        verify(outboxMapper).markDead(eq(17L), eq(5), anyString());
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<SendResult<String, String>> successfulSend() {
        return CompletableFuture.completedFuture(mock(SendResult.class));
    }

    private static CompletableFuture<SendResult<String, String>> failedSend() {
        return CompletableFuture.failedFuture(new IllegalStateException("kafka unavailable"));
    }

    private static CommunitySearchOutboxDO event(int attempts) {
        return new CommunitySearchOutboxDO().setId(17L).setEventType("POST_CHANGED").setEventKey("post:42:7")
                .setAggregateType("community_post").setAggregateId(42L).setPayload("{\"postId\":42}").setAttempts(attempts);
    }
}

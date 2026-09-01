package cn.iocoder.yudao.module.commerce.service.outbox.kafka;

import cn.iocoder.yudao.module.commerce.dal.dataobject.outbox.CommerceKafkaConsumerReceiptDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.outbox.CommerceKafkaConsumerReceiptMapper;
import cn.iocoder.yudao.module.commerce.enums.outbox.CommerceKafkaReceiptStatusEnum;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommerceKafkaReceiptServiceTest {
    @Mock private CommerceKafkaConsumerReceiptMapper mapper;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void beginClaimsNewReceipt() {
        when(mapper.selectByGroupAndEvent("group", 11L)).thenReturn(null);
        CommerceKafkaReceiptService service = service();

        CommerceKafkaReceiptService.BeginResult result = service.begin(message());

        assertThat(result.claimed()).isTrue();
        assertThat(result.receipt().getStatus()).isEqualTo(CommerceKafkaReceiptStatusEnum.PROCESSING.getStatus());
        verify(mapper).insert(any(CommerceKafkaConsumerReceiptDO.class));
    }

    @Test
    void beginDoesNotProcessAlreadyLeasedReceipt() {
        CommerceKafkaConsumerReceiptDO existing = receipt(CommerceKafkaReceiptStatusEnum.PROCESSING.getStatus())
                .setProcessingTime(LocalDateTime.now());
        when(mapper.selectByGroupAndEvent("group", 11L)).thenReturn(existing);
        when(mapper.claimProcessing(eq(7L), eq(3), any(), any())).thenReturn(0);
        CommerceKafkaReceiptService service = service();

        CommerceKafkaReceiptService.BeginResult result = service.begin(message());

        assertThat(result.claimed()).isFalse();
        verify(mapper).claimProcessing(eq(7L), eq(3), any(), any());
    }

    @Test
    void beginReclaimsFailedReceiptAfterReplay() {
        CommerceKafkaConsumerReceiptDO existing = receipt(CommerceKafkaReceiptStatusEnum.REQUEUED.getStatus());
        when(mapper.selectByGroupAndEvent("group", 11L)).thenReturn(existing);
        when(mapper.claimProcessing(eq(7L), eq(3), any(), any())).thenReturn(1);
        CommerceKafkaReceiptService service = service();

        CommerceKafkaReceiptService.BeginResult result = service.begin(message());

        assertThat(result.claimed()).isTrue();
        assertThat(result.receipt().getAttempts()).isEqualTo(3);
    }

    @Test
    void processedReceiptIsAcknowledgedAsDuplicateWithoutClaim() {
        CommerceKafkaConsumerReceiptDO existing = receipt(CommerceKafkaReceiptStatusEnum.PROCESSED.getStatus());
        when(mapper.selectByGroupAndEvent("group", 11L)).thenReturn(existing);
        CommerceKafkaReceiptService service = service();

        CommerceKafkaReceiptService.BeginResult result = service.begin(message());

        assertThat(result.claimed()).isFalse();
        verify(mapper, never()).claimProcessing(any(), anyInt(), any(), any());
    }

    private CommerceKafkaReceiptService service() {
        return new CommerceKafkaReceiptService(mapper, kafkaTemplate, "events", "group",
                new SimpleMeterRegistry(), Duration.ofMinutes(10));
    }

    private static CommerceKafkaEventMessage message() {
        return new CommerceKafkaEventMessage().setEventId(11L).setEventType("ORDER_PAID")
                .setEventKey("ORDER_PAID:11").setAggregateId(11L).setPayload("{}");
    }

    private static CommerceKafkaConsumerReceiptDO receipt(int status) {
        return new CommerceKafkaConsumerReceiptDO().setId(7L).setConsumerGroup("group")
                .setEventId(11L).setEventType("ORDER_PAID").setEventKey("ORDER_PAID:11")
                .setStatus(status).setAttempts(2).setReceivedTime(LocalDateTime.now());
    }
}

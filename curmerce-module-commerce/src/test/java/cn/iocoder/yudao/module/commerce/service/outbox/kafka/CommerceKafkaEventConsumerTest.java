package cn.iocoder.yudao.module.commerce.service.outbox.kafka;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.commerce.dal.dataobject.outbox.CommerceKafkaConsumerReceiptDO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CommerceKafkaEventConsumerTest {
    private final CommerceKafkaReceiptService receipts = mock(CommerceKafkaReceiptService.class);
    private final CommerceKafkaEventDispatcher dispatcher = mock(CommerceKafkaEventDispatcher.class);
    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
    private final CommerceKafkaEventConsumer consumer = new CommerceKafkaEventConsumer(receipts, dispatcher,
            new SimpleMeterRegistry());

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void establishesEnvelopeTenantForReceiptAndHandlerAndRestoresThreadContext() {
        TenantContextHolder.setTenantId(99L);
        TenantContextHolder.setIgnore(true);
        CommerceKafkaConsumerReceiptDO receipt = new CommerceKafkaConsumerReceiptDO().setId(7L).setAttempts(1);
        when(receipts.begin(any())).thenAnswer(invocation -> {
            assertThat(TenantContextHolder.getTenantId()).isEqualTo(42L);
            assertThat(TenantContextHolder.isIgnore()).isFalse();
            return new CommerceKafkaReceiptService.BeginResult(receipt, true);
        });
        when(dispatcher.dispatch(any())).thenAnswer(invocation -> {
            assertThat(TenantContextHolder.getTenantId()).isEqualTo(42L);
            return 1;
        });

        consumer.consume(record("42"), acknowledgment);

        verify(receipts).processed(7L);
        verify(acknowledgment).acknowledge();
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(99L);
        assertThat(TenantContextHolder.isIgnore()).isTrue();
    }

    @Test
    void rejectsMissingOrNonNumericTenantBeforeTouchingTheReceiptLedger() {
        assertThat(CommerceKafkaEventConsumer.parseTenantId(null)).isNull();
        assertThat(CommerceKafkaEventConsumer.parseTenantId("default")).isNull();
        assertThat(CommerceKafkaEventConsumer.parseTenantId("-1")).isNull();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> consumer.consume(record("default"), acknowledgment))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(receipts, dispatcher, acknowledgment);
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void clearsTenantWhenHandlerFailsSoTheContainerThreadCannotLeakIt() {
        CommerceKafkaConsumerReceiptDO receipt = new CommerceKafkaConsumerReceiptDO().setId(8L).setAttempts(2);
        when(receipts.begin(any())).thenReturn(new CommerceKafkaReceiptService.BeginResult(receipt, true));
        when(dispatcher.dispatch(any())).thenThrow(new IllegalStateException("handler down"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> consumer.consume(record("7"), acknowledgment))
                .isInstanceOf(IllegalStateException.class);

        verify(receipts).failed(eq(8L), eq(2), any());
        assertThat(TenantContextHolder.getTenantId()).isNull();
        verifyNoInteractions(acknowledgment);
    }

    private static ConsumerRecord<String, String> record(String tenantId) {
        String body = "{\"eventId\":11,\"tenantId\":\"" + tenantId
                + "\",\"eventType\":\"ORDER_PAID\",\"eventKey\":\"ORDER_PAID:11\",\"payload\":\"{}\"}";
        return new ConsumerRecord<>("curmerce.events.v1", 0, 0L, "42:11", body);
    }
}

package cn.iocoder.yudao.module.commerce.service.outbox;

import cn.iocoder.yudao.module.commerce.dal.dataobject.outbox.CommerceOutboxEventDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.outbox.CommerceOutboxMapper;
import cn.iocoder.yudao.module.commerce.enums.outbox.CommerceOutboxStatusEnum;
import cn.iocoder.yudao.module.commerce.service.outbox.mq.CommerceOutboxStreamMessagePublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommerceOutboxPublisherServiceImplTest {

    @Mock private CommerceOutboxMapper outboxMapper;
    @Mock private CommerceOutboxStreamMessagePublisher messagePublisher;
    @InjectMocks private CommerceOutboxPublisherServiceImpl service;

    @Test
    void publishPending_publishesAndMarksPublished() {
        CommerceOutboxEventDO event = pendingEvent(1001L);
        when(outboxMapper.selectPendingForUpdate(10)).thenReturn(List.of(event));
        when(outboxMapper.markPublished(eq(1001L), any())).thenReturn(1);

        assertEquals(1, service.publishPending(10));

        verify(messagePublisher).publish(event);
        ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxMapper).markPublished(eq(1001L), timeCaptor.capture());
        assertEquals(0, timeCaptor.getValue().getNano());
    }

    @Test
    void publishPending_schedulesRetryWithBackoffWhenPublishFails() {
        CommerceOutboxEventDO event = pendingEvent(1002L);
        when(outboxMapper.selectPendingForUpdate(10)).thenReturn(List.of(event));
        org.mockito.Mockito.doThrow(new RuntimeException("redis down"))
                .when(messagePublisher).publish(event);
        when(outboxMapper.markRetry(eq(1002L), eq(1), any(), anyString())).thenReturn(1);

        assertEquals(0, service.publishPending(10));

        ArgumentCaptor<LocalDateTime> retryCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(outboxMapper).markRetry(eq(1002L), eq(1), retryCaptor.capture(), eq("redis down"));
        LocalDateTime expected = LocalDateTime.now().plusSeconds(30);
        LocalDateTime actual = retryCaptor.getValue();
        assertEquals(0, actual.getNano());
        assertEquals(expected.getYear(), actual.getYear());
        assertEquals(expected.getMinute(), actual.getMinute());
        verify(outboxMapper, never()).markDead(any(), anyString());
    }

    @Test
    void publishPending_marksDeadAfterMaxAttempts() {
        CommerceOutboxEventDO event = pendingEvent(1003L).setAttempts(4);
        when(outboxMapper.selectPendingForUpdate(10)).thenReturn(List.of(event));
        org.mockito.Mockito.doThrow(new RuntimeException("redis down"))
                .when(messagePublisher).publish(event);
        when(outboxMapper.markDead(eq(1003L), anyString())).thenReturn(1);

        assertEquals(0, service.publishPending(10));

        verify(outboxMapper).markDead(eq(1003L), eq("redis down"));
        verify(outboxMapper, never()).markRetry(any(), anyInt(), any(), anyString());
    }

    @Test
    void publishPending_skipsEventWhenAnotherPublisherAlreadyMarked() {
        CommerceOutboxEventDO event = pendingEvent(1004L);
        when(outboxMapper.selectPendingForUpdate(10)).thenReturn(List.of(event));
        when(outboxMapper.markPublished(eq(1004L), any())).thenReturn(0);

        assertEquals(0, service.publishPending(10));

        verify(messagePublisher).publish(event);
        verify(outboxMapper, never()).markRetry(any(), anyInt(), any(), anyString());
        verify(outboxMapper, never()).markDead(any(), anyString());
    }

    private static CommerceOutboxEventDO pendingEvent(Long id) {
        return new CommerceOutboxEventDO()
                .setId(id).setEventType("ORDER_PAID").setEventKey("ORDER_PAID:" + id)
                .setAggregateType("commerce_order").setAggregateId(id)
                .setPayload("{}").setStatus(CommerceOutboxStatusEnum.PENDING.getStatus())
                .setAttempts(0);
    }
}

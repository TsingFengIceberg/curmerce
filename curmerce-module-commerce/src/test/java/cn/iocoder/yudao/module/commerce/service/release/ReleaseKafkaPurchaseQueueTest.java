package cn.iocoder.yudao.module.commerce.service.release;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePurchaseReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePurchaseRespVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.release.CommerceReleasePurchaseCommandDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.release.CommerceReleasePurchaseCommandMapper;
import cn.iocoder.yudao.module.commerce.service.outbox.CommerceOutboxEventAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.RELEASE_RESERVATION_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseKafkaPurchaseQueueTest {
    private final CommerceReleasePurchaseCommandMapper commandMapper = mock(CommerceReleasePurchaseCommandMapper.class);
    private final CommerceOutboxEventAppender outboxAppender = mock(CommerceOutboxEventAppender.class);
    private final ReleaseService releaseService = mock(ReleaseService.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private ReleaseKafkaPurchaseQueue queue;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        doAnswer(invocation -> {
            TransactionCallback callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(transactions).execute(any(TransactionCallback.class));
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any(Consumer.class));
        queue = new ReleaseKafkaPurchaseQueue(commandMapper, outboxAppender, releaseService, transactions,
                new EmptyObjectProvider<>(), true, "kafka", 3, 1, 8, 10);
    }

    @Test
    void enqueueReturnsTheExistingTicketOnlyForEquivalentIdempotentCommand() {
        CommerceReleasePurchaseCommandDO existing = command(9L).setTicket("12345678-1234-1234-1234-123456789012");
        when(commandMapper.selectByBuyerItemAndKey(7L, 11L, "purchase-key")).thenReturn(existing);

        assertEquals(existing.getTicket(), queue.enqueue(7L, request()));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(7L,
                request().setQuantity(2)));

        verify(commandMapper, never()).insert(any(CommerceReleasePurchaseCommandDO.class));
        verifyNoDispatch();
    }

    @Test
    void duplicateKafkaDeliveryCreatesOnePurchaseAndCompletesTheSameCommand() {
        CommerceReleasePurchaseCommandDO command = command(9L);
        when(commandMapper.selectByIdForUpdate(9L)).thenReturn(command);
        when(releaseService.purchase(eq(7L), any(ReleasePurchaseReqVO.class)))
                .thenReturn(new ReleasePurchaseRespVO().setPurchaseId(31L).setOrderId(41L));

        queue.consume(9L);
        queue.consume(9L);

        assertEquals(ReleaseKafkaPurchaseQueue.COMPLETED, command.getStatus());
        assertEquals(1, command.getAttempts());
        assertNotNull(command.getResult());
        verify(releaseService, times(1)).purchase(eq(7L), any(ReleasePurchaseReqVO.class));
        verify(commandMapper, times(2)).updateById(command);
        verifyNoDispatch();
    }

    @Test
    void retriableReservationFailureIsRedeliveredThroughANewOutboxSnapshot() {
        CommerceReleasePurchaseCommandDO command = command(9L);
        when(commandMapper.selectByIdForUpdate(9L)).thenReturn(command);
        when(releaseService.purchase(eq(7L), any(ReleasePurchaseReqVO.class)))
                .thenThrow(ServiceExceptionUtil.exception(RELEASE_RESERVATION_UNAVAILABLE));

        queue.consume(9L);

        assertEquals(ReleaseKafkaPurchaseQueue.RETRY_WAIT, command.getStatus());
        assertEquals(1, command.getAttempts());
        assertNotNull(command.getRetryAt());
        assertEquals(null, command.getProcessingToken());
        command.setRetryAt(LocalDateTime.now().minusSeconds(1));
        when(commandMapper.selectRecoverable(any(LocalDateTime.class), eq(100))).thenReturn(List.of(command));

        queue.recoverDueCommands();

        assertEquals(ReleaseKafkaPurchaseQueue.QUEUED, command.getStatus());
        assertEquals(2, command.getDispatchVersion());
        verify(outboxAppender, times(1)).appendState(any(), eq(9L), any());
    }

    @Test
    void finalFailureIsTerminalAndAnExpiredProcessingLeaseIsSafelyRedispatched() {
        CommerceReleasePurchaseCommandDO command = command(9L).setAttempts(2);
        when(commandMapper.selectByIdForUpdate(9L)).thenReturn(command);
        when(releaseService.purchase(eq(7L), any(ReleasePurchaseReqVO.class)))
                .thenThrow(new IllegalStateException("unexpected failure"));

        queue.consume(9L);

        assertEquals(ReleaseKafkaPurchaseQueue.FAILED, command.getStatus());
        assertEquals(3, command.getAttempts());
        assertEquals("unexpected failure", command.getLastError());

        command.setStatus(ReleaseKafkaPurchaseQueue.PROCESSING).setProcessingToken("lost-worker")
                .setProcessingDeadline(LocalDateTime.now().minusSeconds(1));
        when(commandMapper.selectRecoverable(any(LocalDateTime.class), eq(100))).thenReturn(List.of(command));

        queue.recoverDueCommands();

        assertEquals(ReleaseKafkaPurchaseQueue.QUEUED, command.getStatus());
        assertEquals(2, command.getDispatchVersion());
        assertEquals(null, command.getProcessingToken());
        assertEquals(null, command.getProcessingDeadline());
        verify(outboxAppender, times(1)).appendState(any(), eq(9L), any());
    }

    private CommerceReleasePurchaseCommandDO command(Long id) {
        return new CommerceReleasePurchaseCommandDO().setId(id).setTicket("12345678-1234-1234-1234-123456789012")
                .setBuyerUserId(7L).setItemId(11L).setQuantity(1).setAddressId(17L).setIdempotencyKey("purchase-key")
                .setStatus(ReleaseKafkaPurchaseQueue.QUEUED).setAttempts(0).setDispatchVersion(1);
    }

    private ReleasePurchaseReqVO request() {
        return new ReleasePurchaseReqVO().setItemId(11L).setQuantity(1).setAddressId(17L).setIdempotencyKey("purchase-key");
    }

    private void verifyNoDispatch() {
        verify(outboxAppender, never()).appendState(any(), anyLong(), any());
    }

    private static final class EmptyObjectProvider<T> implements ObjectProvider<T> {
        @Override public T getObject(Object... args) { return null; }
        @Override public T getIfAvailable() { return null; }
        @Override public T getIfUnique() { return null; }
        @Override public T getIfAvailable(java.util.function.Supplier<T> supplier) { return supplier.get(); }
        @Override public T getIfUnique(java.util.function.Supplier<T> supplier) { return supplier.get(); }
        @Override public java.util.Iterator<T> iterator() { return Collections.emptyIterator(); }
    }
}

package cn.iocoder.yudao.module.commerce.service.release;

import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePurchaseReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePurchaseRespVO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReleasePurchaseQueueTest {
    private ReleasePurchaseQueue queue;

    @AfterEach
    void closeQueue() {
        if (queue != null) queue.shutdown();
    }

    @Test
    void statusIsBoundToTheEnqueuingUser() throws Exception {
        ReleaseService service = mock(ReleaseService.class);
        when(service.purchase(eq(7L), any())).thenReturn(new ReleasePurchaseRespVO().setOrderId(900L));
        queue = new ReleasePurchaseQueue(service, 10, 1, new SimpleMeterRegistry());

        String ticket = queue.enqueue(7L, request());
        awaitCompleted(ticket, 7L);

        assertThrows(ReleasePurchaseQueue.TicketAccessDeniedException.class,
                () -> queue.status(ticket, 8L));
        assertEquals(900L, queue.status(ticket, 7L).result().getOrderId());
    }

    @Test
    void failedPurchaseIsRetainedAsAStableTicketResult() throws Exception {
        ReleaseService service = mock(ReleaseService.class);
        when(service.purchase(eq(7L), any())).thenThrow(new IllegalStateException("库存不足"));
        queue = new ReleasePurchaseQueue(service, 10, 1, new SimpleMeterRegistry());

        String ticket = queue.enqueue(7L, request());
        ReleasePurchaseQueue.Ticket ticketResult = awaitCompleted(ticket, 7L);

        assertEquals("FAILED", ticketResult.status());
        assertEquals("库存不足", ticketResult.error());
    }

    @Test
    void sameUserItemAndIdempotencyKeyShareOneLocalTicket() {
        ReleaseService service = mock(ReleaseService.class);
        queue = new ReleasePurchaseQueue(service, 10, 1, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        String first = queue.enqueue(7L, request());
        String second = queue.enqueue(7L, request());

        assertEquals(first, second);
        // The second enqueue must reuse the first ticket, so the worker is
        // allowed to invoke the purchase operation exactly once.
        verify(service, timeout(500).times(1)).purchase(eq(7L), any());
    }

    @Test
    void differentUsersDoNotShareAnIdempotencyTicket() {
        ReleaseService service = mock(ReleaseService.class);
        queue = new ReleasePurchaseQueue(service, 10, 1, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        String first = queue.enqueue(7L, request());
        String second = queue.enqueue(8L, request());

        assertNotEquals(first, second);
    }

    private ReleasePurchaseQueue.Ticket awaitCompleted(String ticket, Long userId) throws Exception {
        for (int i = 0; i < 50; i++) {
            ReleasePurchaseQueue.Ticket result = queue.status(ticket, userId);
            if (!"QUEUED".equals(result.status()) && !"PROCESSING".equals(result.status())) return result;
            TimeUnit.MILLISECONDS.sleep(10);
        }
        fail("queue did not finish in time");
        return null;
    }

    private ReleasePurchaseReqVO request() {
        return new ReleasePurchaseReqVO().setItemId(1L).setQuantity(1).setAddressId(2L).setIdempotencyKey("key");
    }
}

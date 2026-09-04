package cn.iocoder.yudao.module.commerce.service.release;

import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePurchaseReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ReleaseDistributedPurchaseQueueTest {
    @Test
    void pendingRecoveryUsesTheConfiguredIdleWindowForItsClaim() throws Exception {
        ReleaseDistributedPurchaseQueue queue = new ReleaseDistributedPurchaseQueue(mock(ReleaseService.class),
                mock(StringRedisTemplate.class), new TestObjectProvider<>(), true);
        java.lang.reflect.Field pendingIdleSeconds = ReleaseDistributedPurchaseQueue.class.getDeclaredField("pendingIdleSeconds");
        pendingIdleSeconds.setAccessible(true);
        pendingIdleSeconds.set(queue, 47L);

        assertEquals(java.time.Duration.ofSeconds(47), queue.pendingClaimDuration());
    }

    @Test
    void statusRejectsAUserWhoDoesNotOwnTheRedisTicket() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.entries(anyString())).thenReturn(Map.of("owner", "7", "status", "QUEUED",
                "attempts", "0", "acceptedAt", Instant.now().toString()));
        ReleaseDistributedPurchaseQueue queue = new ReleaseDistributedPurchaseQueue(mock(ReleaseService.class), redis,
                new TestObjectProvider<>(), true);

        String ticket = "12345678-1234-1234-1234-123456789012";
        assertThrows(ReleasePurchaseQueue.TicketAccessDeniedException.class, () -> queue.status(ticket, 8L));
        assertEquals("QUEUED", queue.status(ticket, 7L).status());
    }

    @Test
    void retryRejectsAUserWhoDoesNotOwnTheRedisTicket() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.entries(anyString())).thenReturn(Map.of("owner", "7", "userId", "7", "status", "FAILED",
                "request", "{}", "acceptedAt", Instant.now().toString()));
        ReleaseDistributedPurchaseQueue queue = new ReleaseDistributedPurchaseQueue(mock(ReleaseService.class), redis,
                new TestObjectProvider<>(), true);

        assertThrows(ReleasePurchaseQueue.TicketAccessDeniedException.class, () -> queue.retry("12345678-1234-1234-1234-123456789012", 8L));
        verify(redis, never()).execute(any(), anyList(), any());
    }

    @Test
    void enqueueReturnsStableUnavailableWhenRedisAndCleanupBothFail() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        doThrow(new IllegalStateException("redis down"))
                .when(redis).execute(any(), anyList(), any());
        doThrow(new IllegalStateException("redis still down"))
                .when(redis).delete(anyString());
        ReleaseDistributedPurchaseQueue queue = new ReleaseDistributedPurchaseQueue(mock(ReleaseService.class), redis,
                new TestObjectProvider<>(), true);

        assertThrows(ReleaseDistributedPurchaseQueue.QueueUnavailableException.class,
                () -> queue.enqueue(7L, new ReleasePurchaseReqVO().setItemId(1L).setQuantity(1)
                        .setAddressId(2L).setIdempotencyKey("queue-fault-key")));
    }

    private static final class TestObjectProvider<T> implements org.springframework.beans.factory.ObjectProvider<T> {
        @Override public T getObject(Object... args) { return null; }
        @Override public T getIfAvailable() { return null; }
        @Override public T getIfUnique() { return null; }
        @Override public T getIfAvailable(java.util.function.Supplier<T> supplier) { return supplier.get(); }
        @Override public T getIfUnique(java.util.function.Supplier<T> supplier) { return supplier.get(); }
        @Override public java.util.Iterator<T> iterator() { return java.util.Collections.emptyIterator(); }
    }
}

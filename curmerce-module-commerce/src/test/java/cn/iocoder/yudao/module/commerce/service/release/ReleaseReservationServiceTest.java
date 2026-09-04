package cn.iocoder.yudao.module.commerce.service.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class ReleaseReservationServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;

    @Test
    void reserveUsesAtomicResultAndMapsFailureCodes() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString())).thenReturn(true);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(2L);

        ReleaseReservationService service = new ReleaseReservationService(redis, true);

        assertEquals(ReleaseReservationService.ReservationResult.RESERVED,
                service.reserve(1L, 2L, 3L, 1, 2, 3));
    }

    @Test
    void keyedReserveRecognizesAnUnfinishedReservationWithoutConsumingAgain() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString())).thenReturn(true);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(-4L);

        ReleaseReservationService service = new ReleaseReservationService(redis, true);

        assertEquals(ReleaseReservationService.ReservationResult.ALREADY_RESERVED,
                service.reserve(1L, 2L, 3L, 1, 2, 3, "request-0001"));
    }

    @Test
    void keyedReserveRejectsAKeyReusedWithDifferentQuantity() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString())).thenReturn(true);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(-5L);

        ReleaseReservationService service = new ReleaseReservationService(redis, true);

        assertEquals(ReleaseReservationService.ReservationResult.IDEMPOTENCY_CONFLICT,
                service.reserve(1L, 2L, 3L, 1, 2, 3, "request-0001"));
    }

    @Test
    void reserveRejectsRedisUnavailableWithoutPretendingStockWasReserved() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString())).thenThrow(new IllegalStateException("redis down"));

        ReleaseReservationService service = new ReleaseReservationService(redis, true);

        assertEquals(ReleaseReservationService.ReservationResult.UNAVAILABLE,
                service.reserve(1L, 2L, 3L, 1, 2, 3));
    }

    @Test
    void releaseAndCommitMapLuaAcknowledgement() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString())).thenReturn(1L);
        ReleaseReservationService service = new ReleaseReservationService(redis, true);

        assertTrue(service.release(1L, 2L, 3L, 1));
        assertTrue(service.commit(1L, 2L, 1));
    }

    @Test
    void releaseAndCommitFailClosedWhenRedisDoesNotAcknowledge() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString())).thenReturn(0L);
        ReleaseReservationService service = new ReleaseReservationService(redis, true);

        assertFalse(service.release(1L, 2L, 3L, 1));
        assertFalse(service.commit(1L, 2L, 1));
    }

    @Test
    void keyedReleaseUsesOneAtomicScriptSoRetriesCannotRestoreStockTwice() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString())).thenReturn(1L);
        ReleaseReservationService service = new ReleaseReservationService(redis, true);

        assertTrue(service.release(1L, 2L, 3L, 1, "request-0001"));
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(DefaultRedisScript.class), keys.capture(), anyString());
        assertEquals(4, keys.getValue().size());
        assertFalse(keys.getValue().get(3).contains("request-0001"));
    }

    @Test
    void committedPurchaseRestoreUsesPurchaseFenceAndDatabaseStockFallback() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString())).thenReturn(1L);
        ReleaseReservationService service = new ReleaseReservationService(redis, true);

        assertTrue(service.restoreCommittedPurchase(1L, 2L, 3L, 4L, 1, 5));
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(DefaultRedisScript.class), keys.capture(), anyString(), anyString(), anyString());
        assertEquals(3, keys.getValue().size());
        assertTrue(keys.getValue().get(2).endsWith(":4"));
    }

    @Test
    void invalidReservationArgumentsFailBeforeTouchingRedis() {
        ReleaseReservationService service = new ReleaseReservationService(redis, true);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.reserve(1L, 2L, 3L, 0, 1, 2, "key"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.release(1L, 2L, 3L, 0, "key"));
        verify(redis, org.mockito.Mockito.never()).execute(any(DefaultRedisScript.class), anyList(), anyString());
    }

    @Test
    void disabledReservationIsACompatibleNoop() {
        ReleaseReservationService service = new ReleaseReservationService(redis, false);

        assertEquals(ReleaseReservationService.ReservationResult.DISABLED,
                service.reserve(1L, 2L, 3L, 1, 2, 3));
        assertTrue(service.release(1L, 2L, 3L, 1));
        assertTrue(service.commit(1L, 2L, 1));
    }
}

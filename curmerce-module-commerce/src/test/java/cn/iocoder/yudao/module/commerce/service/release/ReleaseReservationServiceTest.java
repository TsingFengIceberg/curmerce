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
    void disabledReservationIsACompatibleNoop() {
        ReleaseReservationService service = new ReleaseReservationService(redis, false);

        assertEquals(ReleaseReservationService.ReservationResult.DISABLED,
                service.reserve(1L, 2L, 3L, 1, 2, 3));
        assertTrue(service.release(1L, 2L, 3L, 1));
        assertTrue(service.commit(1L, 2L, 1));
    }
}

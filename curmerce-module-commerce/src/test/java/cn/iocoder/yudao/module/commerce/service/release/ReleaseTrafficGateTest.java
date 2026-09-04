package cn.iocoder.yudao.module.commerce.service.release;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReleaseTrafficGateTest {
    @Test
    void disabledGateAllowsTrafficWithoutRedis() {
        ReleaseTrafficGate gate = new ReleaseTrafficGate(null, false, 1, 1, 1);
        assertEquals(ReleaseTrafficGate.Result.DISABLED, gate.allow(1L, 2L));
    }

    @Test
    void unavailableRedisFailsClosed() {
        ReleaseTrafficGate gate = new ReleaseTrafficGate(null, true, 1, 1, 1);
        assertEquals(ReleaseTrafficGate.Result.UNAVAILABLE, gate.allow(1L, 2L));
    }
}

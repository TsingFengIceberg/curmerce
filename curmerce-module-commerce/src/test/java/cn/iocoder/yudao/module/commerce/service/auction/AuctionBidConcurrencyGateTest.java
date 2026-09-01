package cn.iocoder.yudao.module.commerce.service.auction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionBidConcurrencyGateTest {
    @Mock private StringRedisTemplate redis;

    @Test
    void mapsLuaDecisionsToStableDomainResults() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);
        AuctionBidConcurrencyGate gate = new AuctionBidConcurrencyGate(redis, true, 3600);

        assertThat(gate.tryAccept(1L, 120L, 110L, 42L, "bid-key-1"))
                .isEqualTo(AuctionBidConcurrencyGate.Result.ACCEPTED);
    }

    @Test
    void rejectsDuplicateAndUnavailableRequests() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(2L);
        AuctionBidConcurrencyGate gate = new AuctionBidConcurrencyGate(redis, true, 3600);
        assertThat(gate.tryAccept(1L, 120L, 110L, 42L, "bid-key-1"))
                .isEqualTo(AuctionBidConcurrencyGate.Result.DUPLICATE);

        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("redis down"));
        assertThat(gate.tryAccept(1L, 120L, 110L, 42L, "bid-key-2"))
                .isEqualTo(AuctionBidConcurrencyGate.Result.UNAVAILABLE);
    }

    @Test
    void disabledGateDoesNotTouchRedis() {
        AuctionBidConcurrencyGate gate = new AuctionBidConcurrencyGate(redis, false, 3600);
        assertThat(gate.tryAccept(1L, 120L, 110L, 42L, "bid-key-1"))
                .isEqualTo(AuctionBidConcurrencyGate.Result.DISABLED);
    }
}

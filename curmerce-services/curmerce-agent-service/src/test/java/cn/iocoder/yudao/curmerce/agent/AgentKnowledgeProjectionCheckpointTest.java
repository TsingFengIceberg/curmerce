package cn.iocoder.yudao.curmerce.agent;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class AgentKnowledgeProjectionCheckpointTest {
    private static final String PREFIX = "curmerce:agent:knowledge:v1:projection:";

    @Test
    void skipsDuplicateAndOlderEventsAndRejectsLocalLockContention() throws Exception {
        AgentKnowledgeProjectionCheckpoint checkpoint = new AgentKnowledgeProjectionCheckpoint();

        AgentKnowledgeProjectionCheckpoint.ProjectionLease first = checkpoint.acquire("community", 9L, 11L);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Throwable> competing = executor.submit(() -> {
                try {
                    checkpoint.acquire("community", 9L, 12L);
                    return null;
                } catch (Throwable ex) {
                    return ex;
                }
            });
            assertThat(competing.get()).isInstanceOf(IllegalStateException.class).hasMessageContaining("busy");
        } finally {
            executor.shutdownNow();
        }
        first.markApplied();
        first.close();

        try (AgentKnowledgeProjectionCheckpoint.ProjectionLease duplicate = checkpoint.acquire("community", 9L, 11L);
             AgentKnowledgeProjectionCheckpoint.ProjectionLease older = checkpoint.acquire("community", 9L, 10L);
             AgentKnowledgeProjectionCheckpoint.ProjectionLease newer = checkpoint.acquire("community", 9L, 12L)) {
            assertThat(duplicate.skipped()).isTrue();
            assertThat(older.skipped()).isTrue();
            assertThat(newer.skipped()).isFalse();
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void persistsRedisCheckpointOnlyAfterApplyAndReleasesOnlyItsOwnLock() {
        String sourceKey = "product:15";
        String scopedPrefix = AgentRequestContext.key(PREFIX.substring(0, PREFIX.length() - 1));
        String lockKey = scopedPrefix + ":lock:" + sourceKey;
        String checkpointKey = scopedPrefix + ":checkpoint:" + sourceKey;
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq(lockKey), anyString(), eq(Duration.ofSeconds(120)))).thenReturn(true);
        when(values.get(checkpointKey)).thenReturn(null);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString())).thenReturn(1L);
        AgentKnowledgeProjectionCheckpoint checkpoint = new AgentKnowledgeProjectionCheckpoint(redis,
                new AgentKnowledgeProjectionProperties(true, null, null, null, 1_000L, 3L, 120L));

        AgentKnowledgeProjectionCheckpoint.ProjectionLease lease = checkpoint.acquire("product", 15L, 21L);
        verify(values, never()).set(eq(checkpointKey), eq("21"));

        lease.markApplied();
        lease.close();

        ArgumentCaptor<String> lockedToken = ArgumentCaptor.forClass(String.class);
        verify(values).setIfAbsent(eq(lockKey), lockedToken.capture(), eq(Duration.ofSeconds(120)));
        ArgumentCaptor<DefaultRedisScript> script = ArgumentCaptor.forClass(DefaultRedisScript.class);
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> releasedToken = ArgumentCaptor.forClass(String.class);
        verify(redis, org.mockito.Mockito.times(2)).execute(script.capture(), keys.capture(), releasedToken.capture());
        assertThat(script.getAllValues().get(0).getScriptAsString()).contains("incoming > current")
                .contains("redis.call('set', KEYS[1], ARGV[1])");
        assertThat(keys.getAllValues().get(0)).containsExactly(checkpointKey);
        assertThat(releasedToken.getAllValues().get(0)).isEqualTo("21");
        assertThat(keys.getAllValues().get(1)).containsExactly(lockKey);
        assertThat(releasedToken.getAllValues().get(1)).isEqualTo(lockedToken.getValue());
        assertThat(script.getAllValues().get(1).getScriptAsString()).contains("redis.call('get', KEYS[1]) == ARGV[1]")
                .contains("redis.call('del', KEYS[1])");
        verify(redis, never()).delete(lockKey);
    }
}

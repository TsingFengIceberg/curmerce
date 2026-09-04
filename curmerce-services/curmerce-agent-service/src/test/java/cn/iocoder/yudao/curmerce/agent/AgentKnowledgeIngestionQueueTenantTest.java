package cn.iocoder.yudao.curmerce.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Verifies that durable ingestion carries and enforces tenant ownership. */
class AgentKnowledgeIngestionQueueTenantTest {
    @AfterEach
    void clearContext() {
        AgentRequestContext.clear();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void submitPersistsTenantAndRegistersTenantScopedWorkerStream() throws Exception {
        AgentRequestContext.bind("Bearer tenant-user", "tenant-a");
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        ObjectProvider<io.micrometer.core.instrument.MeterRegistry> metricsProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(metricsProvider.getIfAvailable()).thenReturn(null);
        when(redis.execute(any(), anyList(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        AgentKnowledgeIngestionQueue queue = new AgentKnowledgeIngestionQueue(new AgentKnowledgeStore(),
                10, 1, metricsProvider, redisProvider, new ObjectMapper(), 3, 500, 30_000, 30, 120);
        try {
            AgentKnowledgeIngestionQueue.Job job = queue.submit("product", List.of(
                    new AgentKnowledgeStore.SourceDocument("p-1", "商品", null)));

            assertThat(job.status()).isEqualTo("QUEUED");
            org.mockito.ArgumentCaptor<List> keys = org.mockito.ArgumentCaptor.forClass(List.class);
            org.mockito.ArgumentCaptor<String> tenant = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(redis).execute(any(), keys.capture(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), tenant.capture());
            assertThat(keys.getValue()).hasSize(3).last().isEqualTo("curmerce:agent:knowledge:v1:tenants");
            assertThat(tenant.getValue()).isEqualTo("tenant-a");
        } finally {
            queue.shutdown();
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void durableStatusDoesNotCrossTenantBoundary() {
        AgentRequestContext.bind("internal", "tenant-a");
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.entries(anyString())).thenReturn(Map.of("tenantId", "tenant-b", "status", "QUEUED"));
        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        ObjectProvider<io.micrometer.core.instrument.MeterRegistry> metricsProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(redis);
        when(metricsProvider.getIfAvailable()).thenReturn(null);

        AgentKnowledgeIngestionQueue queue = new AgentKnowledgeIngestionQueue(new AgentKnowledgeStore(),
                10, 1, metricsProvider, redisProvider, new ObjectMapper(), 3, 500, 30_000, 30, 120);
        try {
            assertThat(queue.status("job-1")).isNull();
        } finally {
            queue.shutdown();
        }
    }
}

package cn.iocoder.yudao.curmerce.agent;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentRetrievalServiceTest {

    @Test
    void assistDegradesWhenBothReadOnlySourcesAreUnavailable() {
        AgentServiceProperties properties = new AgentServiceProperties("http://127.0.0.1:1",
                "http://127.0.0.1:2", Duration.ofMillis(100), Duration.ofMillis(100));
        AgentRetrievalService service = new AgentRetrievalService(RestClient.builder(), properties,
                CircuitBreakerRegistry.ofDefaults());

        AgentAssistRespDTO response = service.assist("manual coffee grinder");

        assertEquals(List.of("core", "community"), response.getDegradedSources());
        assertEquals(0, response.getProducts().size());
        assertEquals(0, response.getCommunityPosts().size());
        assertFalse(response.isModelBacked());
    }
}

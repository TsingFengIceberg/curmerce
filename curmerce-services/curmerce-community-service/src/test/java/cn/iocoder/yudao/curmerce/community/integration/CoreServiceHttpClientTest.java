package cn.iocoder.yudao.curmerce.community.integration;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

import java.time.Duration;

import static cn.iocoder.yudao.module.community.enums.ErrorCodeConstants.CORE_SERVICE_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoreServiceHttpClientTest {

    @Test
    void unavailableCoreMapsToStableDomainError() {
        CoreServiceProperties properties = new CoreServiceProperties("http://127.0.0.1:1",
                "0123456789abcdef0123456789abcdef", Duration.ofMillis(100), Duration.ofMillis(100));
        CoreServiceHttpClient client = new CoreServiceHttpClient(RestClient.builder(), properties,
                CircuitBreakerRegistry.ofDefaults());

        ServiceException error = assertThrows(ServiceException.class, () -> client.getMember(1L));

        assertEquals(CORE_SERVICE_UNAVAILABLE.getCode(), error.getCode());
    }

    @Test
    void repeatedTransportFailuresOpenCoreCircuit() {
        CoreServiceProperties properties = new CoreServiceProperties("http://127.0.0.1:1",
                "0123456789abcdef0123456789abcdef", Duration.ofMillis(100), Duration.ofMillis(100));
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(2).minimumNumberOfCalls(2).failureRateThreshold(50).build());
        CoreServiceHttpClient client = new CoreServiceHttpClient(RestClient.builder(), properties, registry);

        assertThrows(ServiceException.class, () -> client.getMember(1L));
        assertThrows(ServiceException.class, () -> client.getMember(1L));

        assertEquals(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN,
                registry.circuitBreaker("coreService").getState());
    }
}

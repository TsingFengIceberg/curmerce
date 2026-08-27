package cn.iocoder.yudao.curmerce.gateway;

import cn.iocoder.yudao.curmerce.cloud.api.CloudHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TraceIdGlobalFilterTest {

    private final TraceIdGlobalFilter filter = new TraceIdGlobalFilter();

    @Test
    void preservesSafeIncomingTraceId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/app-api/community/post/page")
                .header(CloudHeaders.TRACE_ID, "trace_test_1234"));
        AtomicReference<String> downstream = new AtomicReference<>();

        filter.filter(exchange, current -> {
            downstream.set(current.getRequest().getHeaders().getFirst(CloudHeaders.TRACE_ID));
            return Mono.empty();
        }).block();

        assertEquals("trace_test_1234", downstream.get());
        assertEquals("trace_test_1234", exchange.getResponse().getHeaders().getFirst(CloudHeaders.TRACE_ID));
    }

    @Test
    void replacesUnsafeTraceId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/")
                .header(CloudHeaders.TRACE_ID, "bad value"));
        AtomicReference<String> downstream = new AtomicReference<>();

        filter.filter(exchange, current -> {
            downstream.set(current.getRequest().getHeaders().getFirst(CloudHeaders.TRACE_ID));
            return Mono.empty();
        }).block();

        assertNotNull(downstream.get());
        assertEquals(32, downstream.get().length());
    }
}

package cn.iocoder.yudao.curmerce.gateway;

import cn.iocoder.yudao.curmerce.cloud.api.CloudHeaders;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GatewayUnavailableExceptionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GatewayUnavailableExceptionHandler handler = new GatewayUnavailableExceptionHandler(objectMapper);

    @Test
    void connectFailureReturnsStructuredServiceUnavailable() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/app-api/community/post/page"));
        exchange.getResponse().getHeaders().set(CloudHeaders.TRACE_ID, "trace_gateway_down");

        handler.handle(exchange, new IllegalStateException(new ConnectException("refused"))).block();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        JsonNode body = objectMapper.readTree(exchange.getResponse().getBodyAsString().block());
        assertEquals(503, body.path("status").asInt());
        assertEquals("trace_gateway_down", body.path("traceId").asText());
        assertEquals("/app-api/community/post/page", body.path("path").asText());
    }

    @Test
    void unrelatedFailureIsPropagated() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/app-api/test"));
        IllegalArgumentException error = new IllegalArgumentException("bad request");

        IllegalArgumentException propagated = assertThrows(IllegalArgumentException.class,
                () -> handler.handle(exchange, error).block());

        assertEquals(error, propagated);
    }
}

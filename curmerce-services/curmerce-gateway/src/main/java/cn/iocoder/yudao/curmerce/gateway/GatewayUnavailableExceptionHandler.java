package cn.iocoder.yudao.curmerce.gateway;

import cn.iocoder.yudao.curmerce.cloud.api.CloudHeaders;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayUnavailableExceptionHandler implements WebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GatewayUnavailableExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (!hasConnectFailure(error) || exchange.getResponse().isCommitted()) {
            return Mono.error(error);
        }
        exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String traceId = exchange.getResponse().getHeaders().getFirst(CloudHeaders.TRACE_ID);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        body.put("error", HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase());
        body.put("message", "Downstream service is temporarily unavailable");
        body.put("path", exchange.getRequest().getPath().value());
        body.put("traceId", traceId);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(toJson(body));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private byte[] toJson(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException ignored) {
            return "{\"status\":503,\"error\":\"Service Unavailable\"}".getBytes(StandardCharsets.UTF_8);
        }
    }

    private static boolean hasConnectFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ConnectException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

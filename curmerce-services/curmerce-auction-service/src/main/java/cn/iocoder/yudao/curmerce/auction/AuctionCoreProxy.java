package cn.iocoder.yudao.curmerce.auction;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.util.Set;

/**
 * Transitional HTTP boundary: Auction owns the public route and resilience
 * policy while Core remains the source of truth for the current state machine.
 */
@Component
@Slf4j
public class AuctionCoreProxy {
    private static final Set<String> FORWARDED_HEADERS = Set.of(
            "Authorization", "tenant-id", "X-Curmerce-Trace-Id", "Content-Type", "Accept");

    private final RestClient client;
    private final CircuitBreaker circuitBreaker;

    public AuctionCoreProxy(RestClient.Builder builder, AuctionServiceProperties properties,
                             CircuitBreakerRegistry circuitBreakerRegistry) {
        this(builder, properties, circuitBreakerRegistry, true);
    }

    AuctionCoreProxy(RestClient.Builder builder, AuctionServiceProperties properties,
                     CircuitBreakerRegistry circuitBreakerRegistry, boolean configureTimeouts) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        RestClient.Builder configured = builder.baseUrl(properties.coreBaseUrl());
        if (configureTimeouts) {
            configured = configured.requestFactory(factory);
        }
        this.client = configured.build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("coreAuctionService");
    }

    public ResponseEntity<byte[]> forward(HttpMethod method, String path, String query,
                                           HttpHeaders incomingHeaders, byte[] body) {
        try {
            return circuitBreaker.executeSupplier(() -> execute(method, path, query, incomingHeaders, body));
        } catch (RuntimeException ex) {
            log.warn("auction core unavailable: breakerState={}, path={}, reason={}",
                    circuitBreaker.getState(), path, ex.getMessage());
            byte[] response = "{\"code\":503,\"msg\":\"拍卖服务暂时不可用\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8").body(response);
        }
    }

    private ResponseEntity<byte[]> execute(HttpMethod method, String path, String query,
                                           HttpHeaders incomingHeaders, byte[] body) {
        String target = path + (query == null || query.isBlank() ? "" : "?" + query);
        return client.method(method).uri(URI.create(target)).headers(headers -> {
            FORWARDED_HEADERS.forEach(name -> {
                String value = incomingHeaders.getFirst(name);
                if (value != null) headers.set(name, value);
            });
        }).body(body == null ? new byte[0] : body).exchange((request, response) -> {
            HttpHeaders responseHeaders = new HttpHeaders();
            response.getHeaders().forEach((name, values) -> {
                if (!HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(name)
                        && !HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name)) {
                    responseHeaders.put(name, values);
                }
            });
            return new ResponseEntity<>(response.getBody().readAllBytes(), responseHeaders,
                    response.getStatusCode());
        });
    }
}

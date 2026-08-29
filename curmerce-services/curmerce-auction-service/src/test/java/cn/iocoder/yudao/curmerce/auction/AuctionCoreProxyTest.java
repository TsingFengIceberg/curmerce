package cn.iocoder.yudao.curmerce.auction;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AuctionCoreProxyTest {
    @Test
    void forwardsResponseAndBoundaryHeaders() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://core.test/app-api/commerce/auction/page?pageNo=1&pageSize=2"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(header("tenant-id", "1"))
                .andExpect(header("X-Curmerce-Trace-Id", "auction-test-trace"))
                .andExpect(queryParam("pageNo", "1"))
                .andExpect(queryParam("pageSize", "2"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"total\":1}}",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        AuctionCoreProxy proxy = proxyFor(builder, "http://core.test", false);
        HttpHeaders incoming = new HttpHeaders();
        incoming.set("Authorization", "Bearer test-token");
        incoming.set("tenant-id", "1");
        incoming.set("X-Curmerce-Trace-Id", "auction-test-trace");

        ResponseEntity<byte[]> response = proxy.forward(HttpMethod.GET,
                "/app-api/commerce/auction/page", "pageNo=1&pageSize=2", incoming, new byte[0]);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("{\"code\":0,\"data\":{\"total\":1}}",
                new String(response.getBody(), StandardCharsets.UTF_8));
        server.verify();
    }

    @Test
    void mapsCoreTransportFailureTo503() {
        AuctionCoreProxy proxy = proxyFor(RestClient.builder(), "http://127.0.0.1:1", true);

        ResponseEntity<byte[]> response = proxy.forward(HttpMethod.GET,
                "/app-api/commerce/auction/page", null, new HttpHeaders(), new byte[0]);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertTrue(new String(response.getBody(), StandardCharsets.UTF_8).contains("拍卖服务暂时不可用"));
    }

    private AuctionCoreProxy proxyFor(RestClient.Builder builder, String baseUrl, boolean configureTimeouts) {
        AuctionServiceProperties properties = new AuctionServiceProperties(baseUrl,
                Duration.ofMillis(200), Duration.ofMillis(500));
        return new AuctionCoreProxy(builder, properties, CircuitBreakerRegistry.ofDefaults(), configureTimeouts);
    }
}

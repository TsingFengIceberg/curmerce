package cn.iocoder.yudao.curmerce.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;

class SpringAiCompatibleChatClientTest {
    @Test
    void boundedProviderBodyIsRejectedBeforeItCanBeBufferedBeyondTheLimit() {
        HttpResponse.BodySubscriber<String> subscriber =
                AgentBoundedResponseBodyHandler.utf8(4).apply(null);
        java.util.concurrent.Flow.Subscription subscription = mock(java.util.concurrent.Flow.Subscription.class);
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap("12345".getBytes(java.nio.charset.StandardCharsets.UTF_8))));

        ExecutionException error = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
                () -> subscriber.getBody().toCompletableFuture().get());
        assertEquals("provider response exceeds 4 bytes", error.getCause().getMessage());
        verify(subscription).cancel();
    }

    @Test
    void parsesNormalProviderAnswerAndUsage() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> response = response(200,
                "{\"choices\":[{\"message\":{\"content\":\"根据检索结果，咖啡机价格为 12 元。\"}}],"
                        + "\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":6}}");
        doReturn(response).when(http).send(any(), any());

        SpringAiCompatibleChatClient client = client(http);
        SpringAiCompatibleChatClient.ModelAnswer answer = client.complete("咖啡机多少钱", "商品价格 12 元");

        assertEquals("根据检索结果，咖啡机价格为 12 元。", answer.answer());
        assertEquals(8, answer.usage().promptTokens());
        assertEquals(6, answer.usage().completionTokens());
        verify(http).send(any(), any());
    }

    @Test
    void parsesToolCallsAndSupportsToolResultRoundTrip() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> toolCall = response(200,
                "{\"choices\":[{\"message\":{\"content\":\"\",\"tool_calls\":[{"
                        + "\"id\":\"call-1\",\"type\":\"function\",\"function\":{"
                        + "\"name\":\"product-search\",\"arguments\":\"{\\\"query\\\":\\\"咖啡\\\"}\"}}]}}]}");
        HttpResponse<String> finalAnswer = response(200,
                "{\"choices\":[{\"message\":{\"content\":\"找到 1 个相关商品。\"}}]}");
        doReturn(toolCall, finalAnswer).when(http).send(any(), any());

        SpringAiCompatibleChatClient client = client(http);
        SpringAiCompatibleChatClient.ModelAnswer first = client.complete("找咖啡", "商品上下文");
        assertEquals("product-search", first.toolCalls().get(0).name());
        assertEquals("咖啡", first.toolCalls().get(0).arguments().path("query").asText());

        SpringAiCompatibleChatClient.ModelAnswer second = client.completeWithToolResults("找咖啡", "商品上下文",
                first, List.of(new SpringAiCompatibleChatClient.ToolResult("call-1", "product-search", true,
                        "[{\"id\":1}]")));
        assertEquals("找到 1 个相关商品。", second.answer());
        verify(http, times(2)).send(any(), any());
    }

    @Test
    void preservesEveryAssistantToolTurnAcrossMultipleRounds() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse<String> firstToolCall = response(200,
                "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{"
                        + "\"id\":\"call-1\",\"type\":\"function\",\"function\":{"
                        + "\"name\":\"product-search\",\"arguments\":\"{\\\"query\\\":\\\"咖啡\\\"}\"}}]}}]}");
        HttpResponse<String> secondToolCall = response(200,
                "{\"choices\":[{\"message\":{\"content\":null,\"tool_calls\":[{"
                        + "\"id\":\"call-2\",\"type\":\"function\",\"function\":{"
                        + "\"name\":\"community-search\",\"arguments\":\"{\\\"query\\\":\\\"咖啡\\\"}\"}}]}}]}");
        HttpResponse<String> finalAnswer = response(200,
                "{\"choices\":[{\"message\":{\"content\":\"已综合商品与社区内容。\"}}]}");
        doReturn(firstToolCall, secondToolCall, finalAnswer).when(http).send(any(), any());

        SpringAiCompatibleChatClient client = client(http);
        SpringAiCompatibleChatClient.ModelAnswer first = client.complete("找咖啡", "商品上下文");
        SpringAiCompatibleChatClient.ModelAnswer second = client.completeWithToolResults("找咖啡", "商品上下文", first,
                List.of(new SpringAiCompatibleChatClient.ToolResult("call-1", "product-search", true, "[商品]")));
        SpringAiCompatibleChatClient.ModelAnswer third = client.completeWithToolResults("找咖啡", "商品上下文", second,
                List.of(new SpringAiCompatibleChatClient.ToolResult("call-2", "community-search", true, "[社区]")));

        assertEquals("已综合商品与社区内容。", third.answer());
        assertEquals(7, third.transcript().size());
        assertEquals("call-1", ((java.util.List<?>) third.transcript().get(2).get("tool_calls")).size() == 1
                ? ((java.util.Map<?, ?>) ((java.util.List<?>) third.transcript().get(2).get("tool_calls")).get(0)).get("id") : "");
        assertEquals("call-2", ((java.util.Map<?, ?>) ((java.util.List<?>) third.transcript().get(4).get("tool_calls")).get(0)).get("id"));
        assertEquals("tool", third.transcript().get(5).get("role"));
        verify(http, times(3)).send(any(), any());
    }

    @Test
    void rejectsToolCallsWithoutProviderStableId() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(response(200, "{\"choices\":[{\"message\":{\"tool_calls\":[{\"function\":{\"name\":\"product-search\",\"arguments\":\"{}\"}}]}}]}"))
                .when(http).send(any(), any());

        assertThrows(SpringAiCompatibleChatClient.ModelUnavailableException.class,
                () -> client(http).complete("q", "c"));
    }

    @Test
    void retriesTransientProviderFailureWithBoundedAttempts() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(response(503, "{\"error\":{\"message\":\"busy\"}}"),
                response(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}")
        ).when(http).send(any(), any());

        assertEquals("ok", client(http).complete("q", "c").answer());
        verify(http, times(2)).send(any(), any());
    }

    @Test
    void retriesRateLimitAndServerFailuresButStopsAtBoundedAttemptCount() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(response(429, "{\"error\":{\"message\":\"rate limited\"}}"),
                response(503, "{\"error\":{\"message\":\"busy\"}}"),
                response(502, "{\"error\":{\"message\":\"busy\"}}"))
                .when(http).send(any(), any());

        assertThrows(SpringAiCompatibleChatClient.ModelUnavailableException.class,
                () -> client(http).complete("q", "c"));
        verify(http, times(3)).send(any(), any());
    }

    @Test
    void timeoutIsRetriedAndExposedAsSafeModelUnavailableFailure() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doThrow(new HttpTimeoutException("read timed out")).when(http).send(any(), any());

        assertThrows(SpringAiCompatibleChatClient.ModelUnavailableException.class,
                () -> client(http).complete("q", "c"));
        verify(http, times(3)).send(any(), any());
    }

    @Test
    void malformedJsonAndOversizedResponsesAreRejected() throws Exception {
        HttpClient malformed = mock(HttpClient.class);
        doReturn(response(200, "{not-json")).when(malformed).send(any(), any());
        assertThrows(SpringAiCompatibleChatClient.ModelUnavailableException.class,
                () -> client(malformed).complete("q", "c"));

        HttpClient oversized = mock(HttpClient.class);
        doReturn(response(200, "x".repeat(1_048_577))).when(oversized).send(any(), any());
        assertThrows(SpringAiCompatibleChatClient.ModelUnavailableException.class,
                () -> client(oversized).complete("q", "c"));
    }

    @Test
    void rejectsEmptyProviderAnswer() throws Exception {
        HttpClient http = mock(HttpClient.class);
        doReturn(response(200, "{\"choices\":[{\"message\":{\"content\":\"\"}}]}")
        ).when(http).send(any(), any());

        assertThrows(SpringAiCompatibleChatClient.ModelUnavailableException.class,
                () -> client(http).complete("q", "c"));
    }

    private static SpringAiCompatibleChatClient client(HttpClient http) {
        AgentServiceProperties properties = new AgentServiceProperties(
                "http://core", "http://community", Duration.ofMillis(100), Duration.ofMillis(500),
                true, false, "http://model/v1", "", "curmerce-local", 0D, 0D, "internal",
                false, "http://embedding/v1", "", "embedding", 2, 12000,
                0L, 0D, false, "", "", "", 30, "redis", "http://vector", "idx", "",
                false, "", "", "");
        return new SpringAiCompatibleChatClient(properties, new AgentUsageRecorder(new SimpleMeterRegistry()),
                new ObjectMapper(), new AgentToolRegistry(), http);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        doReturn(status).when(response).statusCode();
        doReturn(body).when(response).body();
        return response;
    }
}

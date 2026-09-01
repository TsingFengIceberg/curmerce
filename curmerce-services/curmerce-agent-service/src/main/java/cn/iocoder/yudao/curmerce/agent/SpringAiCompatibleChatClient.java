package cn.iocoder.yudao.curmerce.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Spring-AI-compatible model boundary. It speaks the standard
 * /chat/completions contract so Ollama, Spring AI providers, and hosted
 * OpenAI-compatible endpoints can be swapped without touching Agent tools.
 */
@Component
public class SpringAiCompatibleChatClient {
    private final AgentServiceProperties properties;
    private final AgentUsageRecorder usageRecorder;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SpringAiCompatibleChatClient(AgentServiceProperties properties, AgentUsageRecorder usageRecorder,
                                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.usageRecorder = usageRecorder;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    public boolean enabled() { return properties.modelEnabled(); }

    public ModelAnswer complete(String query, String context) {
        if (!enabled()) return null;
        Instant started = Instant.now();
        try {
            Map<String, Object> body = Map.of("model", properties.modelName(), "temperature", 0.2,
                    "messages", List.of(Map.of("role", "system", "content", "你是 Curmerce 的购物助手，只能基于提供的检索上下文回答，不得编造库存、价格或订单事实。"),
                            Map.of("role", "user", "content", "检索上下文：\n" + context + "\n\n用户问题：" + query)));
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.modelBaseUrl() + "/chat/completions"))
                    .timeout(properties.readTimeout()).header("Content-Type", "application/json");
            if (!properties.modelApiKey().isBlank()) request.header("Authorization", "Bearer " + properties.modelApiKey());
            HttpResponse<String> response = httpClient.send(request.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) throw new IllegalStateException("model HTTP " + response.statusCode());
            JsonNode root = objectMapper.readTree(response.body());
            String answer = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (answer.isBlank()) throw new IllegalStateException("model response has no answer");
            int prompt = root.path("usage").path("prompt_tokens").asInt(estimateTokens(query + context));
            int completion = root.path("usage").path("completion_tokens").asInt(estimateTokens(answer));
            Duration latency = Duration.between(started, Instant.now());
            double cost = prompt * properties.inputCostPerThousandTokens() / 1000D
                    + completion * properties.outputCostPerThousandTokens() / 1000D;
            return new ModelAnswer(answer, usageRecorder.record(properties.modelName(), prompt, completion, latency, cost));
        } catch (Exception ex) {
            usageRecorder.record("model-error", estimateTokens(query + context), 0,
                    Duration.between(started, Instant.now()), 0D);
            throw new ModelUnavailableException(ex.getMessage(), ex);
        }
    }

    private static int estimateTokens(String value) { return Math.max(1, (value == null ? 0 : value.length()) / 4); }

    public record ModelAnswer(String answer, AgentUsageRecorder.Usage usage) { }
    public static class ModelUnavailableException extends RuntimeException {
        public ModelUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}

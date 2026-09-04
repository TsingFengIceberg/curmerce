package cn.iocoder.yudao.curmerce.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spring-AI-compatible model boundary. It speaks the standard
 * /chat/completions contract so Ollama, Spring AI providers, and hosted
 * OpenAI-compatible endpoints can be swapped without touching Agent tools.
 */
@Component
@ConditionalOnProperty(prefix = "curmerce.agent", name = "spring-ai-enabled", havingValue = "false", matchIfMissing = true)
public class SpringAiCompatibleChatClient implements AgentChatModel {
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BASE_DELAY_MILLIS = 50L;
    private final AgentServiceProperties properties;
    private final AgentUsageRecorder usageRecorder;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AgentToolRegistry toolRegistry;

    public SpringAiCompatibleChatClient(AgentServiceProperties properties, AgentUsageRecorder usageRecorder,
                                       ObjectMapper objectMapper) {
        this(properties, usageRecorder, objectMapper, new AgentToolRegistry(),
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
    }

    @Autowired
    public SpringAiCompatibleChatClient(AgentServiceProperties properties, AgentUsageRecorder usageRecorder,
                                       ObjectMapper objectMapper, AgentToolRegistry toolRegistry) {
        this(properties, usageRecorder, objectMapper, toolRegistry,
                HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build());
    }

    SpringAiCompatibleChatClient(AgentServiceProperties properties, AgentUsageRecorder usageRecorder,
                                 ObjectMapper objectMapper, AgentToolRegistry toolRegistry,
                                 HttpClient httpClient) {
        this.properties = properties;
        this.usageRecorder = usageRecorder;
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
        this.httpClient = httpClient;
    }

    public boolean enabled() { return properties.modelEnabled(); }

    public ModelAnswer complete(String query, String context) {
        if (!enabled()) return null;
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(systemMessage());
        messages.add(userMessage("检索上下文：\n" + safe(context) + "\n\n用户问题：" + safe(query)));
        return request(messages, query + context);
    }

    /** Completes the model's tool-call turn by feeding typed tool results back into the same conversation. */
    public ModelAnswer completeWithToolResults(String query, String context, ModelAnswer previous,
                                               List<ToolResult> results) {
        if (!enabled()) return null;
        if (previous == null || previous.toolCalls() == null || previous.toolCalls().isEmpty()) {
            throw new IllegalArgumentException("previous model turn does not contain tool calls");
        }
        List<Map<String, Object>> messages = previous.transcript().isEmpty()
                ? initialTranscript(query, context, previous) : copyTranscript(previous.transcript());
        Set<String> callIds = previous.toolCalls().stream().map(ToolCall::id).collect(java.util.stream.Collectors.toSet());
        Set<String> resultIds = new java.util.HashSet<>();
        for (ToolResult result : results == null ? List.<ToolResult>of() : results) {
            if (result == null || result.callId() == null || result.callId().isBlank() || !callIds.contains(result.callId())) {
                throw new IllegalArgumentException("tool result does not match the preceding model tool call");
            }
            if (!resultIds.add(result.callId())) {
                throw new IllegalArgumentException("duplicate tool result for model tool call");
            }
            messages.add(toolMessage(result));
        }
        if (resultIds.size() != callIds.size()) {
            throw new IllegalArgumentException("every model tool call must receive exactly one result");
        }
        return request(messages, query + context + results);
    }

    private ModelAnswer request(List<Map<String, Object>> messages, Object estimateSource) {
        Instant started = Instant.now();
        try {
            int estimatedPrompt = estimateTokens(String.valueOf(estimateSource));
            double estimatedCost = estimatedPrompt * properties.inputCostPerThousandTokens() / 1000D;
            if (!usageRecorder.allow(AgentRequestContext.principal(), properties.modelName(), estimatedPrompt, estimatedCost)) {
                throw new ModelUnavailableException("Agent 每日模型配额已用尽", null);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", properties.modelName());
            body.put("temperature", 0.2);
            body.put("messages", messages);
            body.put("tools", toolRegistry.openAiDefinitions());
            body.put("tool_choice", "auto");
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.modelBaseUrl() + "/chat/completions"))
                    .timeout(properties.readTimeout()).header("Content-Type", "application/json");
            if (!properties.modelApiKey().isBlank()) request.header("Authorization", "Bearer " + properties.modelApiKey());
            HttpRequest httpRequest = request
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
            HttpResponse<String> response = sendWithRetry(httpRequest);
            if (response.body() == null || response.body().getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                throw new IllegalStateException("model response is too large");
            }
            if (response.statusCode() >= 300) throw new IllegalStateException("model HTTP " + response.statusCode());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode message = root.path("choices").path(0).path("message");
            String answer = message.path("content").asText("").trim();
            List<ToolCall> toolCalls = parseToolCalls(message.path("tool_calls"));
            if (answer.isBlank() && toolCalls.isEmpty()) throw new IllegalStateException("model response has no answer");
            int estimated = estimatedPrompt;
            int prompt = root.path("usage").path("prompt_tokens").asInt(estimated);
            int completion = root.path("usage").path("completion_tokens").asInt(estimateTokens(answer));
            Duration latency = Duration.between(started, Instant.now());
            double cost = prompt * properties.inputCostPerThousandTokens() / 1000D
                    + completion * properties.outputCostPerThousandTokens() / 1000D;
            messages.add(assistantMessage(message, toolCalls));
            return new ModelAnswer(answer, usageRecorder.record(AgentRequestContext.principal(), properties.modelName(),
                    prompt, completion, latency, cost), toolCalls, copyTranscript(messages));
        } catch (Exception ex) {
            usageRecorder.record(AgentRequestContext.principal(), "model-error", estimateTokens(String.valueOf(estimateSource)), 0,
                    Duration.between(started, Instant.now()), 0D);
            throw new ModelUnavailableException(ex.getMessage(), ex);
        }
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                response = httpClient.send(request, AgentBoundedResponseBodyHandler.utf8(MAX_RESPONSE_BYTES));
                if (!retryable(response.statusCode()) || attempt == MAX_ATTEMPTS) return response;
            } catch (IOException ex) {
                if (attempt == MAX_ATTEMPTS) throw ex;
            }
            try {
                Thread.sleep(RETRY_BASE_DELAY_MILLIS * (1L << (attempt - 1)));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            }
        }
        return response;
    }

    private static boolean retryable(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private static Map<String, Object> systemMessage() {
        return Map.of("role", "system", "content", "你是 Curmerce 的购物助手，只能基于提供的检索上下文和工具结果回答，不得编造库存、价格或订单事实。若工具结果不足，明确说明无法确认。引用事实时尽量使用上下文中的 [商品:ID]、[帖子:ID] 或 [知识库:来源:ID] 标记。");
    }
    private static Map<String, Object> userMessage(String content) { return Map.of("role", "user", "content", content); }
    private static String safe(String value) { return value == null ? "" : value; }

    private List<Map<String, Object>> initialTranscript(String query, String context, ModelAnswer previous) {
        List<Map<String, Object>> transcript = new ArrayList<>();
        transcript.add(systemMessage());
        transcript.add(userMessage("检索上下文：\n" + safe(context) + "\n\n用户问题：" + safe(query)));
        transcript.add(assistantMessage(previous.answer(), previous.toolCalls()));
        return transcript;
    }

    private Map<String, Object> assistantMessage(JsonNode message, List<ToolCall> calls) {
        JsonNode content = message == null ? null : message.get("content");
        return assistantMessage(content == null || content.isNull() ? null : content.asText(), calls);
    }

    private Map<String, Object> assistantMessage(String content, List<ToolCall> calls) {
        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        // OpenAI-compatible providers require null rather than an invented
        // textual value for tool-only assistant turns.
        assistant.put("content", content);
        if (calls != null && !calls.isEmpty()) {
            assistant.put("tool_calls", calls.stream().map(call -> {
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", call.name());
                function.put("arguments", call.arguments() == null ? "{}" : call.arguments().toString());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", call.id());
                item.put("type", "function");
                item.put("function", function);
                return item;
            }).toList());
        }
        return assistant;
    }

    private static Map<String, Object> toolMessage(ToolResult result) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("role", "tool");
        tool.put("tool_call_id", result.callId());
        tool.put("content", result.content() == null ? "" : result.content());
        return tool;
    }

    private static List<Map<String, Object>> copyTranscript(List<Map<String, Object>> transcript) {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (Map<String, Object> message : transcript) copy.add(new LinkedHashMap<>(message));
        return copy;
    }

    private static int estimateTokens(String value) { return Math.max(1, (value == null ? 0 : value.length()) / 4); }

    private List<ToolCall> parseToolCalls(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<ToolCall> calls = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            String id = item.path("id").asText("").trim();
            String name = item.path("function").path("name").asText("").trim();
            if (id.isBlank()) throw new IllegalStateException("model tool call has no stable id");
            if (name.isBlank()) continue;
            try {
                JsonNode arguments = item.path("function").path("arguments");
                JsonNode parsed = arguments.isObject() ? arguments : objectMapper.readTree(arguments.asText("{}"));
                if (parsed == null || !parsed.isObject()) throw new IllegalArgumentException("tool arguments must be an object");
                calls.add(new ToolCall(id, name, parsed));
            }
            catch (Exception ex) { throw new IllegalStateException("model tool arguments are invalid", ex); }
        }
        return List.copyOf(calls);
    }

    public record ModelAnswer(String answer, AgentUsageRecorder.Usage usage, List<ToolCall> toolCalls,
                              List<Map<String, Object>> transcript) {
        public ModelAnswer(String answer, AgentUsageRecorder.Usage usage, List<ToolCall> toolCalls) {
            this(answer, usage, toolCalls, List.of());
        }

        public ModelAnswer {
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
            transcript = transcript == null ? List.of() : copyTranscript(transcript);
        }
    }
    public record ToolCall(String id, String name, JsonNode arguments) { }
    public record ToolResult(String callId, String name, boolean success, String content) { }
    public static class ModelUnavailableException extends RuntimeException {
        public ModelUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}

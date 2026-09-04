package cn.iocoder.yudao.curmerce.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Real Spring AI entry point.  The existing compatible HTTP adapter remains
 * the default so local development does not require a provider; enabling this
 * bean switches the same AgentChatModel boundary to Spring AI's ChatClient.
 */
@Component
@ConditionalOnProperty(prefix = "curmerce.agent", name = "spring-ai-enabled", havingValue = "true")
public class SpringAiChatClientAdapter implements AgentChatModel {
    private final ChatClient client;
    private final AgentUsageRecorder usage;
    private final AgentServiceProperties properties;
    private final AgentToolExecutor toolExecutor;
    private final AgentToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final List<ToolCallback> toolCallbacks;

    public SpringAiChatClientAdapter(ChatModel model, AgentUsageRecorder usage, AgentServiceProperties properties,
                                     AgentToolExecutor toolExecutor, AgentToolRegistry toolRegistry,
                                     ObjectMapper objectMapper) {
        this.usage = usage;
        this.properties = properties;
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.toolCallbacks = buildToolCallbacks();
        this.client = ChatClient.builder(model).build();
    }

    @Override
    public boolean enabled() { return properties.modelEnabled() && properties.springAiEnabled(); }

    @Override
    public SpringAiCompatibleChatClient.ModelAnswer complete(String query, String context) {
        if (!enabled()) {
            throw new IllegalStateException("Spring AI Provider 未启用");
        }
        Instant started = Instant.now();
        int estimatedPrompt = estimateTokens(query) + estimateTokens(context);
        double estimatedCost = estimatedPrompt * properties.inputCostPerThousandTokens() / 1000D;
        if (!usage.allow(AgentRequestContext.principal(), properties.modelName(), estimatedPrompt, estimatedCost)) {
            throw new IllegalStateException("Agent 每日模型配额已用尽");
        }
        try {
            String answer = client.prompt()
                    .system("你是 Curmerce 的购物助手，只能基于提供的检索上下文回答，不得编造库存、价格或订单事实。引用事实时尽量使用上下文中的 [商品:ID]、[帖子:ID] 或 [知识库:来源:ID] 标记。")
                    .user("检索上下文：\n" + safe(context) + "\n\n用户问题：" + safe(query))
                    .toolCallbacks(toolCallbacks)
                    .toolContext(java.util.Map.of("authorization", safe(AgentRequestContext.principal())))
                    .call().content();
            return answer(query, context, answer, started);
        } catch (RuntimeException ex) {
            // Keep the reservation and the accounting path symmetric with the
            // OpenAI-compatible adapter. A failed provider call still spends
            // an attempted request budget and must release its in-flight slot.
            usage.record(AgentRequestContext.principal(), "spring-ai-error", estimatedPrompt, 0,
                    Duration.between(started, Instant.now()), 0D);
            throw ex;
        }
    }

    @Override
    public SpringAiCompatibleChatClient.ModelAnswer completeWithToolResults(
            String query, String context, SpringAiCompatibleChatClient.ModelAnswer previous,
            List<SpringAiCompatibleChatClient.ToolResult> results) {
        String toolContext = results == null ? "" : results.stream()
                .map(result -> result.name() + ": " + result.content()).reduce("", (a, b) -> a + "\n" + b);
        return complete(query, safe(context) + "\n工具结果：\n" + toolContext);
    }

    private SpringAiCompatibleChatClient.ModelAnswer answer(String query, String context, String value, Instant started) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank()) throw new IllegalStateException("Spring AI 返回空回答");
        int prompt = estimateTokens(query) + estimateTokens(context);
        int completion = estimateTokens(safe);
        Duration latency = Duration.between(started, Instant.now());
        double cost = prompt * properties.inputCostPerThousandTokens() / 1000D
                + completion * properties.outputCostPerThousandTokens() / 1000D;
        AgentUsageRecorder.Usage recorded = usage.record(AgentRequestContext.principal(), properties.modelName(),
                prompt, completion, latency, cost);
        return new SpringAiCompatibleChatClient.ModelAnswer(safe, recorded, List.of());
    }

    private static int estimateTokens(String value) { return Math.max(1, value == null ? 0 : value.length() / 4); }
    private static String safe(String value) { return value == null ? "" : value; }

    private List<ToolCallback> buildToolCallbacks() {
        return toolRegistry.list().stream().map(descriptor -> {
            String schema = toolRegistry.openAiDefinitions().stream()
                    .filter(item -> descriptor.name().equals(item.get("function") instanceof java.util.Map<?, ?> function
                            ? String.valueOf(function.get("name")) : ""))
                    .map(item -> item.get("function") instanceof java.util.Map<?, ?> function
                            ? writeJson(function.get("parameters")) : "{}")
                    .findFirst().orElse("{}");
            return (ToolCallback) FunctionToolCallback.<String, String>builder(descriptor.name(), (input, context) -> {
                        try {
                            JsonNode arguments = input == null || input.isBlank()
                                    ? objectMapper.createObjectNode() : objectMapper.readTree(input);
                            java.util.Map<String, Object> contextValues = context == null ? java.util.Map.of() : context.getContext();
                            String authorization = String.valueOf(contextValues
                                    .getOrDefault("authorization", AgentRequestContext.principal()));
                            SpringAiCompatibleChatClient.ToolResult result = toolExecutor.executeForModel(
                                    authorization, new SpringAiCompatibleChatClient.ToolCall(
                                            "spring-ai-" + descriptor.name(), descriptor.name(), arguments));
                            return result.content();
                        } catch (RuntimeException ex) {
                            return ex.getMessage() == null ? "工具执行失败" : ex.getMessage();
                        } catch (Exception ex) {
                            return "工具结果序列化失败";
                        }
                    })
                    .description(descriptor.description())
                    .inputSchema(schema)
                    .build();
        }).toList();
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { return "{}"; }
    }
}

package cn.iocoder.yudao.curmerce.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PreDestroy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.Iterator;

/** Executes only registered tools and keeps confirmation checks beside execution. */
@Component
public class AgentToolExecutor {
    private static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)(bearer\\s+|api[_-]?key\\s*[:=]\\s*|password\\s*[:=]\\s*|token\\s*[:=]\\s*)[^\\s,;]+",
            Pattern.CASE_INSENSITIVE);
    private final AgentToolRegistry registry;
    private final AgentCoreClient core;
    private final AgentRetrievalService retrieval;
    private final AgentConfirmationService confirmations;
    private final AgentInputPolicy inputPolicy;
    private final AgentAuditRecorder audit;
    private final long timeoutMillis;
    private final ExecutorService timeoutPool = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "curmerce-agent-tool");
        thread.setDaemon(true);
        return thread;
    });
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AgentRuleCatalog ruleCatalog;

    public AgentToolExecutor(AgentToolRegistry registry, AgentCoreClient core,
                             AgentRetrievalService retrieval, AgentConfirmationService confirmations,
                             AgentInputPolicy inputPolicy, AgentAuditRecorder audit) {
        this(registry, core, retrieval, confirmations, inputPolicy, audit, 3000L);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AgentToolExecutor(AgentToolRegistry registry, AgentCoreClient core,
                             AgentRetrievalService retrieval, AgentConfirmationService confirmations,
                             AgentInputPolicy inputPolicy, AgentAuditRecorder audit,
                             @Value("${curmerce.agent.tool-timeout-ms:3000}") long timeoutMillis) {
        this.registry = registry;
        this.core = core;
        this.retrieval = retrieval;
        this.confirmations = confirmations;
        this.inputPolicy = inputPolicy;
        this.audit = audit;
        this.timeoutMillis = Math.max(100L, Math.min(timeoutMillis, 30_000L));
    }

    public Object execute(String authorization, String name, JsonNode arguments, String confirmationToken) {
        if (name == null || !registry.list().stream().anyMatch(tool -> tool.name().equals(name))) {
            throw new ToolException("Agent 工具不存在");
        }
        JsonNode args = arguments == null ? JsonNodeFactory.instance.objectNode() : arguments;
        if (!args.isObject()) throw new IllegalArgumentException("工具参数必须是 JSON 对象");
        if (args.toString().length() > 8192) throw new IllegalArgumentException("工具参数过长");
        validateArguments(name, args);
        boolean sensitive = registry.list().stream().filter(tool -> tool.name().equals(name))
                .findFirst().orElseThrow().sensitive();
        if (sensitive && (confirmationToken == null || confirmationToken.isBlank())) {
            throw new ToolConfirmationRequiredException("该操作需要用户确认");
        }
        return switch (name) {
            case "order-status" -> core.getOwnOrderStatus(authorization, requiredLong(args, "orderId"));
            case "refund-status" -> core.getOwnRefundStatus(authorization, requiredLong(args, "orderId"));
            case "product-search" -> retrieval.searchProducts(inputPolicy.sanitize(requiredText(args, "query")), authorization);
            case "community-search" -> retrieval.searchCommunity(inputPolicy.sanitize(requiredText(args, "query")), authorization);
            case "platform-rules" -> platformRules();
            case "refund-request" -> refund(authorization, args, confirmationToken);
            default -> throw new ToolException("Agent 工具未实现");
        };
    }

    /** Executes a model-selected tool without leaking internal exception details into the model context. */
    public SpringAiCompatibleChatClient.ToolResult executeForModel(String authorization,
                                                                    SpringAiCompatibleChatClient.ToolCall call) {
        String callId = call == null || call.id() == null || call.id().isBlank()
                ? "call-unknown" : call.id();
        if (call == null) return new SpringAiCompatibleChatClient.ToolResult(callId, "unknown", false, "工具调用为空");
        try {
            Object value = executeWithTimeout(authorization, call.name(), call.arguments(), null);
            audit.record(authorization, "tool:" + call.name(), "success");
            return new SpringAiCompatibleChatClient.ToolResult(callId, call.name(), true, serialize(value));
        } catch (ToolConfirmationRequiredException ex) {
            audit.record(authorization, "tool:" + call.name(), "confirmation-required");
            return new SpringAiCompatibleChatClient.ToolResult(callId, call.name(), false,
                    "该操作需要用户在确认接口中明确确认后才能执行");
        } catch (RuntimeException ex) {
            audit.record(authorization, "tool:" + call.name(), "rejected");
            return new SpringAiCompatibleChatClient.ToolResult(callId, call.name(), false,
                    safeError(ex));
        }
    }

    private Object executeWithTimeout(String authorization, String name, JsonNode arguments, String confirmationToken) {
        Future<Object> future = timeoutPool.submit(() -> execute(authorization, name, arguments, confirmationToken));
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw new ToolTimeoutException("Agent 工具执行超时");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ToolTimeoutException("Agent 工具执行被中断");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new ToolException("Agent 工具执行失败");
        }
    }

    private String serialize(Object value) {
        if (value == null) return "null";
        try { return redact(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value)); }
        catch (Exception ex) { return redact(String.valueOf(value)); }
    }

    private static String safeError(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return "工具执行失败";
        return redact(message.substring(0, Math.min(300, message.length())));
    }

    static String redact(String value) {
        if (value == null || value.isBlank()) return value;
        return AgentInputPolicy.redactSecrets(SECRET_VALUE.matcher(value).replaceAll("$1[REDACTED]"));
    }

    private Object refund(String authorization, JsonNode args, String token) {
        Long orderId = requiredLong(args, "orderId");
        String target = String.valueOf(orderId);
        confirmations.consume(authorization, token, "refund-request", target);
        return core.requestRefund(authorization, orderId, inputPolicy.sanitize(requiredText(args, "reason")));
    }

    /**
     * Validate the complete tool shape before dispatch.  Required-field
     * validation alone is insufficient: an attacker can attach arbitrary
     * nested values to an otherwise valid request and rely on a future tool
     * implementation accidentally interpreting them.  The model and HTTP
     * paths therefore share one small, explicit allow-list for every tool.
     */
    private static void validateArguments(String name, JsonNode args) {
        Set<String> allowed = switch (name) {
            case "order-status", "refund-status" -> Set.of("orderId");
            case "product-search", "community-search" -> Set.of("query");
            case "platform-rules" -> Set.of();
            case "refund-request" -> Set.of("orderId", "reason");
            default -> throw new ToolException("Agent 工具未实现");
        };
        Iterator<String> fields = args.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) throw new IllegalArgumentException("工具参数包含不允许的字段: " + field);
        }
        validateTree(args, 0);
        if ((name.equals("product-search") || name.equals("community-search"))
                && args.path("query").asText("").length() > 2000) {
            throw new IllegalArgumentException("搜索条件过长");
        }
        if (name.equals("refund-request") && args.path("reason").asText("").length() > 1000) {
            throw new IllegalArgumentException("退款原因过长");
        }
    }

    private static void validateTree(JsonNode node, int depth) {
        if (depth > 8) throw new IllegalArgumentException("工具参数嵌套层级过深");
        if (node == null) return;
        if (node.isTextual()) {
            String value = node.textValue();
            if (value != null && value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("工具参数包含控制字符");
            }
        }
        node.elements().forEachRemaining(child -> validateTree(child, depth + 1));
    }

    private Map<String, Object> platformRules() {
        if (ruleCatalog != null) return ruleCatalog.current();
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("paymentTimeoutMinutes", 30);
        rules.put("refundPolicy", "已发货订单需商家审核，退款状态异步更新");
        rules.put("stockSource", "MySQL 事务库存为最终事实来源");
        rules.put("auction", "出价按金额和入库顺序确定领先者");
        return rules;
    }

    private static Long requiredLong(JsonNode args, String name) {
        if (!args.hasNonNull(name) || !args.path(name).canConvertToLong()) throw new IllegalArgumentException(name + " 参数不能为空");
        return args.path(name).longValue();
    }

    private static String requiredText(JsonNode args, String name) {
        String value = args.path(name).asText("").trim();
        if (value.isBlank()) throw new IllegalArgumentException(name + " 参数不能为空");
        return value;
    }

    public static class ToolException extends RuntimeException { public ToolException(String message) { super(message); } }
    public static class ToolConfirmationRequiredException extends RuntimeException { public ToolConfirmationRequiredException(String message) { super(message); } }
    public static class ToolTimeoutException extends RuntimeException { public ToolTimeoutException(String message) { super(message); } }

    @PreDestroy
    void shutdown() { timeoutPool.shutdownNow(); }
}

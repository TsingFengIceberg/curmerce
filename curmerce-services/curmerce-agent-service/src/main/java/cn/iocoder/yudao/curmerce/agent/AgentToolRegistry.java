package cn.iocoder.yudao.curmerce.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Explicit allow-list of typed tools exposed to models and clients. */
@Component
public class AgentToolRegistry {
    private final List<ToolDescriptor> tools = List.of(
            new ToolDescriptor("order-status", "查询当前用户订单状态", false),
            new ToolDescriptor("product-search", "检索商品", false),
            new ToolDescriptor("community-search", "检索社区内容", false),
            new ToolDescriptor("refund-status", "查询当前用户退款状态", false),
            new ToolDescriptor("platform-rules", "查询平台交易规则", false),
            new ToolDescriptor("refund-request", "发起退款", true)
    );

    public List<ToolDescriptor> list() { return tools; }
    public List<Map<String, Object>> openAiDefinitions() {
        return tools.stream().map(tool -> {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            function.put("parameters", parameters(tool.name()));
            return Map.of("type", "function", "function", function);
        }).toList();
    }

    private static Map<String, Object> parameters(String name) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        switch (name) {
            case "product-search", "community-search" -> properties.put("query", Map.of("type", "string"));
            case "order-status", "refund-status" -> properties.put("orderId", Map.of("type", "integer"));
            case "refund-request" -> {
                properties.put("orderId", Map.of("type", "integer"));
                properties.put("reason", Map.of("type", "string"));
            }
            default -> { }
        }
        schema.put("properties", properties);
        switch (name) {
            case "product-search", "community-search" -> schema.put("required", List.of("query"));
            case "order-status", "refund-status" -> schema.put("required", List.of("orderId"));
            case "refund-request" -> schema.put("required", List.of("orderId", "reason"));
            default -> schema.put("required", List.of());
        }
        schema.put("additionalProperties", false);
        return schema;
    }
    public boolean isAllowed(String name, boolean confirmed) {
        return tools.stream().anyMatch(tool -> tool.name().equals(name) && (!tool.sensitive() || confirmed));
    }
    public record ToolDescriptor(String name, String description, boolean sensitive) { }
}

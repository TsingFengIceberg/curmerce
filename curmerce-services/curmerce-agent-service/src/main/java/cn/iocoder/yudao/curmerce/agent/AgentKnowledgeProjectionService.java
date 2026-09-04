package cn.iocoder.yudao.curmerce.agent;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Projects visible product and community state into per-aggregate Agent documents. */
@Service
public class AgentKnowledgeProjectionService {
    private static final String PRODUCT_EVENT = "PRODUCT_CHANGED";
    private static final String POST_EVENT = "POST_CHANGED";
    private static final String PRODUCT_SOURCE = "product";
    private static final String COMMUNITY_SOURCE = "community";

    private final AgentKnowledgeStore store;
    private final AgentKnowledgeProjectionCheckpoint checkpoint;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public AgentKnowledgeProjectionService(AgentKnowledgeStore store, AgentKnowledgeProjectionCheckpoint checkpoint,
                                           ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.store = store;
        this.checkpoint = checkpoint;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Applies one Outbox envelope. The checkpoint advances only after the
     * local durable knowledge mirror has accepted the replacement/deletion.
     */
    public ProjectionResult project(Map<String, Object> envelope) {
        if (envelope == null) throw new IllegalArgumentException("Agent knowledge event envelope is invalid");
        String eventTenant = text(envelope.get("tenantId"));
        if (!eventTenant.isBlank() && !AgentRequestContext.tenantId().equals(AgentRequestContext.normalizeTenant(eventTenant))) {
            throw new IllegalArgumentException("Agent knowledge event tenant does not match the consumer context");
        }
        String eventType = text(envelope.get("eventType"));
        if (!PRODUCT_EVENT.equals(eventType) && !POST_EVENT.equals(eventType)) {
            return new ProjectionResult(eventType, null, 0L, false, true);
        }
        long eventId = positive(envelope.get("eventId"), "eventId");
        Map<String, Object> payload = payload(envelope.get("payload"));
        boolean product = PRODUCT_EVENT.equals(eventType);
        long aggregateId = positive(payload.get(product ? "productId" : "postId"), product ? "productId" : "postId");
        String source = product ? PRODUCT_SOURCE : COMMUNITY_SOURCE;

        try (AgentKnowledgeProjectionCheckpoint.ProjectionLease lease = checkpoint.acquire(source, aggregateId, eventId)) {
            if (lease.skipped()) {
                meterRegistry.counter("curmerce.agent.knowledge.events", "type", eventType, "result", "duplicate").increment();
                return new ProjectionResult(eventType, aggregateId, eventId, false, true);
            }
            boolean visible = product ? number(payload.get("auditStatus")) == 2 && number(payload.get("saleStatus")) == 1
                    : number(payload.get("status")) == 1;
            String documentId = (product ? "product:" : "community:") + aggregateId;
            if (visible) {
                Map<String, Object> metadata = new LinkedHashMap<>(payload);
                metadata.put("sourceEventId", eventId);
                metadata.put("visible", true);
                store.replaceDocument(documentId, source, product ? productText(payload) : postText(payload),
                        objectMapper == null ? JsonNodeFactory.instance.objectNode() : objectMapper.valueToTree(metadata));
            } else {
                store.remove(documentId);
            }
            lease.markApplied();
            meterRegistry.counter("curmerce.agent.knowledge.events", "type", eventType,
                    "result", visible ? "upserted" : "deleted").increment();
            return new ProjectionResult(eventType, aggregateId, eventId, visible, false);
        }
    }

    private static Map<String, Object> payload(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        if (value instanceof String serialized && !serialized.isBlank()) {
            Map<String, Object> parsed = JsonUtils.parseMap(serialized);
            if (parsed != null) return parsed;
        }
        throw new IllegalArgumentException("Agent knowledge event payload is invalid");
    }

    private static String productText(Map<String, Object> product) {
        StringBuilder text = new StringBuilder("商品：").append(value(product, "name"));
        append(text, "副标题", product.get("subtitle"));
        append(text, "分类", product.get("categoryName"));
        append(text, "商品编码", product.get("code"));
        append(text, "描述", product.get("description"));
        if (product.get("skus") instanceof List<?> skus) {
            List<String> rows = new ArrayList<>();
            for (Object item : skus) {
                if (!(item instanceof Map<?, ?> sku)) continue;
                StringBuilder row = new StringBuilder();
                append(row, "SKU", sku.get("code"));
                append(row, "规格", sku.get("specificationValues"));
                append(row, "价格分", sku.get("price"));
                append(row, "库存", sku.get("stock"));
                if (!row.isEmpty()) rows.add(row.toString());
            }
            if (!rows.isEmpty()) text.append("\n可售 SKU：").append(String.join("；", rows));
        }
        return text.toString();
    }

    private static String postText(Map<String, Object> post) {
        StringBuilder text = new StringBuilder("社区帖子：").append(value(post, "title"));
        append(text, "正文", post.get("content"));
        append(text, "话题", post.get("topics"));
        append(text, "关联商品", post.get("productIds"));
        return text.toString();
    }

    private static void append(StringBuilder builder, String label, Object value) {
        String text = text(value);
        if (!text.isBlank()) builder.append("\n").append(label).append("：").append(text);
    }

    private static String value(Map<String, Object> payload, String key) {
        String value = text(payload.get(key));
        return value.isBlank() ? "未命名" : value;
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private static long positive(Object value, String field) {
        try {
            long result = value instanceof Number number ? number.longValue() : Long.parseLong(text(value));
            if (result > 0) return result;
        } catch (RuntimeException ignored) { }
        throw new IllegalArgumentException("Agent knowledge event " + field + " is invalid");
    }

    private static int number(Object value) {
        try { return value instanceof Number number ? number.intValue() : Integer.parseInt(text(value)); }
        catch (RuntimeException ignored) { return 0; }
    }

    public record ProjectionResult(String eventType, Long aggregateId, long eventId, boolean visible, boolean ignored) { }
}

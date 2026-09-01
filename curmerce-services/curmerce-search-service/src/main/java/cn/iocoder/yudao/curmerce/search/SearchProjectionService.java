package cn.iocoder.yudao.curmerce.search;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SearchProjectionService {
    private static final String PRODUCT_EVENT = "PRODUCT_CHANGED";
    private static final String POST_EVENT = "POST_CHANGED";

    private final ElasticsearchIndexClient indexClient;
    private final SearchSourceClient sourceClient;
    private final SearchProperties properties;
    private final MeterRegistry meterRegistry;
    private final Map<String, CacheEntry> pageCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MILLIS = 2_000L;

    public SearchProjectionService(ElasticsearchIndexClient indexClient, SearchSourceClient sourceClient,
                                   SearchProperties properties, MeterRegistry meterRegistry) {
        this.indexClient = indexClient;
        this.sourceClient = sourceClient;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    public synchronized void project(Map<String, Object> envelope) {
        if (!indexClient.enabled()) return;
        if (envelope == null || !(envelope.get("eventId") instanceof Number eventNumber)) {
            throw new IllegalArgumentException("Search event envelope is invalid");
        }
        String eventType = String.valueOf(envelope.get("eventType"));
        Long eventId = eventNumber.longValue();
        String payloadText = envelope.get("payload") instanceof String text
                ? text : JsonUtils.toJsonString(envelope.get("payload"));
        Map<String, Object> payload = JsonUtils.parseMap(payloadText);
        if (payload == null) throw new IllegalArgumentException("Search event payload is invalid");
        if (PRODUCT_EVENT.equals(eventType)) projectProduct(eventId, payload);
        else if (POST_EVENT.equals(eventType)) projectPost(eventId, payload);
        else return;
        pageCache.clear();
        meterRegistry.counter("curmerce.search.projection.events", "type", eventType, "result", "accepted").increment();
    }

    public synchronized RebuildReport rebuildAll() {
        if (!indexClient.enabled()) return new RebuildReport(0, 0, true);
        RebuildReport products = rebuildProducts();
        RebuildReport posts = rebuildPosts();
        return new RebuildReport(products.products(), posts.posts(), true);
    }

    public synchronized RebuildReport rebuildProducts() {
        if (!indexClient.enabled()) return new RebuildReport(0, 0, true);
        indexClient.ensureProductIndex();
        indexClient.deleteAll(properties.productIndex());
        List<Map<String, Object>> documents = sourceClient.fetchProducts();
        indexClient.bulkPut(properties.productIndex(), documents);
        pageCache.clear();
        meterRegistry.counter("curmerce.search.rebuild", "index", "products").increment();
        return new RebuildReport(documents.size(), 0, true);
    }

    public synchronized RebuildReport rebuildPosts() {
        if (!indexClient.enabled()) return new RebuildReport(0, 0, true);
        indexClient.ensurePostIndex();
        indexClient.deleteAll(properties.postIndex());
        List<Map<String, Object>> documents = sourceClient.fetchPosts();
        indexClient.bulkPut(properties.postIndex(), documents);
        pageCache.clear();
        meterRegistry.counter("curmerce.search.rebuild", "index", "posts").increment();
        return new RebuildReport(0, documents.size(), true);
    }

    public ElasticsearchIndexClient.SearchPage searchProducts(String keyword, int page, int size) {
        return cached("products", keyword, page, size,
                () -> indexClient.search(properties.productIndex(), keyword, page, size));
    }

    public ElasticsearchIndexClient.SearchPage searchPosts(String keyword, int page, int size) {
        return cached("posts", keyword, page, size,
                () -> indexClient.search(properties.postIndex(), keyword, page, size));
    }

    public ProjectionReconciliationReport reconcile() {
        if (!indexClient.enabled()) return new ProjectionReconciliationReport(0, 0, 0, 0, true);
        long sourceProducts = sourceClient.fetchProducts().size();
        long sourcePosts = sourceClient.fetchPosts().size();
        long indexedProducts = indexClient.countVisible(properties.productIndex());
        long indexedPosts = indexClient.countVisible(properties.postIndex());
        boolean matched = sourceProducts == indexedProducts && sourcePosts == indexedPosts;
        meterRegistry.counter("curmerce.search.reconciliation", "result", matched ? "matched" : "mismatch").increment();
        return new ProjectionReconciliationReport(sourceProducts, indexedProducts, sourcePosts, indexedPosts, matched);
    }

    private ElasticsearchIndexClient.SearchPage cached(String type, String keyword, int page, int size,
                                                        java.util.function.Supplier<ElasticsearchIndexClient.SearchPage> loader) {
        String key = type + "|" + (keyword == null ? "" : keyword.trim()) + "|" + page + "|" + size;
        long now = System.currentTimeMillis();
        CacheEntry existing = pageCache.get(key);
        if (existing != null && existing.expiresAt() > now) {
            meterRegistry.counter("curmerce.search.cache", "result", "hit").increment();
            return existing.page();
        }
        ElasticsearchIndexClient.SearchPage pageResult = loader.get();
        pageCache.put(key, new CacheEntry(pageResult, now + CACHE_TTL_MILLIS));
        meterRegistry.counter("curmerce.search.cache", "result", "miss").increment();
        return pageResult;
    }

    private void projectProduct(long eventId, Map<String, Object> payload) {
        Long id = longValue(payload.get("productId"));
        if (id == null) throw new IllegalArgumentException("Product search event has no productId");
        Map<String, Object> current = indexClient.get(properties.productIndex(), String.valueOf(id));
        if (isOlder(current, eventId)) return;
        Map<String, Object> document = new HashMap<>(payload);
        document.put("id", id);
        document.put("visible", number(payload.get("auditStatus")) == 2 && number(payload.get("saleStatus")) == 1);
        document.put("sourceEventId", eventId);
        document.put("minPrice", minimumSkuPrice(payload.get("skus")));
        indexClient.ensureProductIndex();
        indexClient.put(properties.productIndex(), String.valueOf(id), document);
    }

    private void projectPost(long eventId, Map<String, Object> payload) {
        Long id = longValue(payload.get("postId"));
        if (id == null) throw new IllegalArgumentException("Post search event has no postId");
        Map<String, Object> current = indexClient.get(properties.postIndex(), String.valueOf(id));
        if (isOlder(current, eventId)) return;
        Map<String, Object> document = new HashMap<>(payload);
        document.put("id", id);
        document.put("visible", number(payload.get("status")) == 1);
        document.put("sourceEventId", eventId);
        indexClient.ensurePostIndex();
        indexClient.put(properties.postIndex(), String.valueOf(id), document);
    }

    private static boolean isOlder(Map<String, Object> current, long eventId) {
        if (current == null) return false;
        Object value = current.get("sourceEventId");
        return value instanceof Number number && number.longValue() >= eventId;
    }

    private static Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private static int number(Object value) {
        return value instanceof Number number ? number.intValue() : value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

    private static Long minimumSkuPrice(Object value) {
        if (!(value instanceof List<?> list)) return null;
        Long minimum = null;
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map) || !(map.get("price") instanceof Number price)) continue;
            minimum = minimum == null ? price.longValue() : Math.min(minimum, price.longValue());
        }
        return minimum;
    }

    public record RebuildReport(long products, long posts, boolean completed) { }
    public record ProjectionReconciliationReport(long sourceProducts, long indexedProducts,
                                                long sourcePosts, long indexedPosts, boolean matched) { }
    private record CacheEntry(ElasticsearchIndexClient.SearchPage page, long expiresAt) { }
}

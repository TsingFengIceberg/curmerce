package cn.iocoder.yudao.curmerce.search;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small typed boundary around the Elasticsearch REST API. */
@Component
public class ElasticsearchIndexClient {
    private final SearchProperties properties;
    private final HttpClient httpClient;

    public ElasticsearchIndexClient(SearchProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder().connectTimeout(properties.requestTimeout()).build();
    }

    @PostConstruct
    void initializeIndices() {
        if (!properties.enabled()) return;
        try {
            ensureIndex(properties.productIndex(), productMapping());
            ensureIndex(properties.postIndex(), postMapping());
        } catch (RuntimeException ex) {
            // The search projection will retry through Kafka after Elasticsearch recovers.
            System.err.println("Curmerce Elasticsearch is not ready: " + ex.getMessage());
        }
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public void ensureProductIndex() {
        ensureIndex(properties.productIndex(), productMapping());
    }

    public void ensurePostIndex() {
        ensureIndex(properties.postIndex(), postMapping());
    }

    public void ensureIndex(String index, Map<String, Object> mapping) {
        if (!properties.enabled()) return;
        try {
            request("PUT", "/" + index, JsonUtils.toJsonString(mapping));
        } catch (ElasticsearchHttpException ex) {
            if (ex.statusCode() != 400 || !ex.body().contains("resource_already_exists_exception")) throw ex;
        }
    }

    public Map<String, Object> get(String index, String id) {
        if (!properties.enabled()) return null;
        try {
            Map<String, Object> response = JsonUtils.parseMap(request("GET", "/" + index + "/_doc/" + id, null));
            Object source = response == null ? null : response.get("_source");
            return source instanceof Map<?, ?> map ? castMap(map) : null;
        } catch (ElasticsearchHttpException ex) {
            if (ex.statusCode() == 404) return null;
            throw ex;
        }
    }

    public void put(String index, String id, Map<String, Object> document) {
        if (!properties.enabled()) return;
        request("PUT", "/" + index + "/_doc/" + id, JsonUtils.toJsonString(document));
    }

    public void delete(String index, String id) {
        if (!properties.enabled()) return;
        try {
            request("DELETE", "/" + index + "/_doc/" + id, null);
        } catch (ElasticsearchHttpException ex) {
            if (ex.statusCode() != 404) throw ex;
        }
    }

    public void deleteAll(String index) {
        if (!properties.enabled()) return;
        Map<String, Object> query = Map.of("query", Map.of("match_all", Map.of()));
        request("POST", "/" + index + "/_delete_by_query?conflicts=proceed", JsonUtils.toJsonString(query));
    }

    public void bulkPut(String index, List<Map<String, Object>> documents) {
        if (!properties.enabled() || documents.isEmpty()) return;
        StringBuilder body = new StringBuilder();
        for (Map<String, Object> document : documents) {
            Object id = document.get("id");
            if (id == null) continue;
            Map<String, Object> action = Map.of("index", Map.of("_index", index, "_id", String.valueOf(id)));
            body.append(JsonUtils.toJsonString(action)).append('\n');
            body.append(JsonUtils.toJsonString(document)).append('\n');
        }
        Map<String, Object> response = JsonUtils.parseMap(request("POST", "/_bulk", body.toString(), "application/x-ndjson"));
        if (Boolean.TRUE.equals(response == null ? null : response.get("errors"))) {
            throw new IllegalStateException("Elasticsearch bulk projection contains item failures");
        }
    }

    public SearchPage search(String index, String keyword, int page, int size) {
        if (!properties.enabled()) return new SearchPage(List.of(), 0L);
        int safePage = Math.max(1, page), safeSize = Math.max(1, Math.min(size, 100));
        Map<String, Object> bool = new HashMap<>();
        if (keyword == null || keyword.isBlank()) {
            bool.put("must", List.of(Map.of("match_all", Map.of())));
        } else {
            bool.put("must", List.of(Map.of("multi_match", Map.of("query", keyword.trim(),
                    "fields", List.of("name^3", "title^3", "subtitle^2", "content", "description", "code")))));
        }
        bool.put("filter", List.of(Map.of("term", Map.of("visible", true))));
        Map<String, Object> query = Map.of("from", (safePage - 1) * safeSize, "size", safeSize,
                "track_total_hits", true, "query", Map.of("bool", bool),
                "sort", List.of(Map.of("_score", "desc"), Map.of("sourceEventId", "desc")));
        Map<String, Object> response = JsonUtils.parseMap(request("POST", "/" + index + "/_search", JsonUtils.toJsonString(query)));
        Map<String, Object> hits = response != null && response.get("hits") instanceof Map<?, ?> map ? castMap(map) : Map.of();
        long total = 0L;
        Object totalValue = hits.get("total");
        if (totalValue instanceof Number number) total = number.longValue();
        else if (totalValue instanceof Map<?, ?> map && map.get("value") instanceof Number number) total = number.longValue();
        List<Map<String, Object>> results = new ArrayList<>();
        if (hits.get("hits") instanceof List<?> list) {
            for (Object hit : list) {
                if (hit instanceof Map<?, ?> map && map.get("_source") instanceof Map<?, ?> source) results.add(castMap(source));
            }
        }
        return new SearchPage(results, total);
    }

    private String request(String method, String path, String body) {
        return request(method, path, body, "application/json");
    }

    private String request(String method, String path, String body, String contentType) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.elasticsearchUrl() + path))
                    .timeout(properties.requestTimeout())
                    .header("Content-Type", contentType);
            if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
            else builder.method(method, HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) throw new ElasticsearchHttpException(response.statusCode(), response.body());
            return response.body();
        } catch (ElasticsearchHttpException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Elasticsearch request failed: " + method + " " + path, ex);
        }
    }

    private Map<String, Object> productMapping() {
        return mapping(Map.of("name", Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword"))),
                "subtitle", Map.of("type", "text"), "description", Map.of("type", "text"),
                "code", Map.of("type", "keyword"), "visible", Map.of("type", "boolean"),
                "sourceEventId", Map.of("type", "long"), "categoryId", Map.of("type", "long"),
                "sellerType", Map.of("type", "integer"), "minPrice", Map.of("type", "long")));
    }

    private Map<String, Object> postMapping() {
        return mapping(Map.of("title", Map.of("type", "text", "fields", Map.of("keyword", Map.of("type", "keyword"))),
                "content", Map.of("type", "text"), "visible", Map.of("type", "boolean"),
                "sourceEventId", Map.of("type", "long"), "authorUserId", Map.of("type", "long")));
    }

    private Map<String, Object> mapping(Map<String, Object> fields) {
        return Map.of("settings", Map.of("number_of_shards", 1, "number_of_replicas", 0),
                "mappings", Map.of("dynamic", false, "properties", fields));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    public record SearchPage(List<Map<String, Object>> results, long total) { }

    public static final class ElasticsearchHttpException extends RuntimeException {
        private final int statusCode;
        private final String body;

        public ElasticsearchHttpException(int statusCode, String body) {
            super("Elasticsearch returned HTTP " + statusCode + ": " + body);
            this.statusCode = statusCode;
            this.body = body;
        }

        public int statusCode() { return statusCode; }
        public String body() { return body; }
    }
}

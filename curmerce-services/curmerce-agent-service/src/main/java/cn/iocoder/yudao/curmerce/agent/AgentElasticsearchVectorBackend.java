package cn.iocoder.yudao.curmerce.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional Elasticsearch-backed knowledge index. Redis remains the default;
 * this adapter deliberately degrades to an empty result when Elasticsearch is
 * unavailable so read-only Agent requests remain safe.
 */
@Component
@ConditionalOnProperty(prefix = "curmerce.agent", name = "vector-backend", havingValue = "elasticsearch")
public class AgentElasticsearchVectorBackend implements AgentVectorBackend {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final AgentServiceProperties properties;
    private final AgentEmbeddingClient embeddingClient;
    private final AtomicBoolean indexReady = new AtomicBoolean();
    private volatile boolean healthy;
    /** The stable alias is switched only after a complete versioned index is written. */
    private volatile String activeIndex;
    private volatile String previousIndex;

    public AgentElasticsearchVectorBackend(RestClient.Builder builder, ObjectMapper objectMapper,
                                           AgentServiceProperties properties,
                                           ObjectProvider<AgentEmbeddingClient> embeddingProvider) {
        this.client = builder.baseUrl(properties.vectorBaseUrl())
                .defaultHeaders(headers -> {
                    if (properties.vectorApiKey() != null && !properties.vectorApiKey().isBlank()) {
                        headers.setBearerAuth(properties.vectorApiKey());
                    }
                }).build();
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.embeddingClient = embeddingProvider.getIfAvailable();
    }

    @Override
    public void upsert(AgentKnowledgeStore.Document document) {
        try {
            write(document, true);
            healthy = true;
        } catch (RuntimeException ex) { healthy = false; throw ex; }
    }

    @Override
    public boolean remove(String id) {
        if (id == null || id.isBlank()) return true;
        try {
            String normalized = id.trim();
            Map<String, Object> query = Map.of("bool", Map.of("should", List.of(
                    Map.of("term", Map.of("id", normalized)),
                    Map.of("prefix", Map.of("id", normalized + "#"))),
                    "minimum_should_match", 1));
            deleteByQuery(query);
            healthy = true;
            return true;
        } catch (HttpClientErrorException.NotFound ignored) {
            healthy = true;
            return true;
        } catch (RuntimeException ex) { healthy = false; return false; }
    }

    @Override
    public void clearSource(String source) {
        if (source == null || source.isBlank()) return;
        try {
            deleteByQuery(Map.of("term", Map.of("source", source.trim())));
            healthy = true;
        } catch (RuntimeException ex) { healthy = false; }
    }

    @Override
    public void clearAll() {
        try {
            deleteByQuery(Map.of("match_all", Map.of()));
            healthy = true;
        } catch (RuntimeException ex) { healthy = false; throw ex; }
    }

    /**
     * Writes every replacement document before deleting stale source rows.
     * This avoids the previous clear-then-upsert window where one ES failure
     * could permanently leave an empty or partial projection. A retry remains
     * safe because document IDs are deterministic.
     */
    @Override
    public void replaceSource(String source, List<AgentKnowledgeStore.Document> documents) {
        String normalized = source == null || source.isBlank() ? "unknown" : source.trim();
        List<AgentKnowledgeStore.Document> safe = documents == null ? List.of() : List.copyOf(documents);
        try {
            int dimensions = safe.stream().map(AgentKnowledgeStore.Document::embedding)
                    .filter(java.util.Objects::nonNull).mapToInt(value -> value.length).findFirst().orElse(64);
            ensureIndex(dimensions);
            for (AgentKnowledgeStore.Document document : safe) write(document, false);
            Map<String, Object> query;
            if (safe.isEmpty()) {
                query = Map.of("term", Map.of("source", normalized));
            } else {
                query = Map.of("bool", Map.of("must", List.of(Map.of("term", Map.of("source", normalized))),
                        "must_not", List.of(Map.of("terms", Map.of("id", safe.stream().map(AgentKnowledgeStore.Document::id).toList())))));
            }
            // refresh=true in deleteByQuery also makes the staged writes
            // visible before this method reports a successful source cutover.
            deleteByQuery(query);
            healthy = true;
        } catch (RuntimeException ex) {
            healthy = false;
            throw ex;
        }
    }

    /**
     * Builds a disposable versioned index and atomically switches the tenant's
     * alias after all documents have been indexed.  The old index is retained
     * until the next successful cutover, so a failed rebuild never exposes an
     * empty projection.  This operation is intentionally explicit and is not
     * used by normal per-document event projection.
     */
    @Override
    public synchronized void rebuildVersioned(List<AgentKnowledgeStore.Document> documents) {
        List<AgentKnowledgeStore.Document> safe = documents == null ? List.of() : List.copyOf(documents);
        int dimensions = safe.stream().map(AgentKnowledgeStore.Document::embedding)
                .filter(java.util.Objects::nonNull).mapToInt(value -> value.length).findFirst().orElse(64);
        String next = baseIndexName() + "-v-" + System.currentTimeMillis();
        try {
            ensureIndexAt(next, dimensions);
            for (AgentKnowledgeStore.Document document : safe) writeTo(next, document, false);
            client.post().uri("/" + next + "/_refresh").retrieve().toBodilessEntity();
            String old = resolveAliasTarget();
            switchAlias(next);
            previousIndex = old;
            activeIndex = next;
            indexReady.set(true);
            healthy = true;
        } catch (RuntimeException ex) {
            healthy = false;
            // The failed version is never made visible. Best-effort cleanup is
            // safe because its name is unique and it is not the active alias.
            try { client.delete().uri("/" + next).retrieve().toBodilessEntity(); } catch (RuntimeException ignored) { }
            throw ex;
        }
    }

    @Override
    public synchronized boolean rollbackVersioned() {
        if (previousIndex == null || previousIndex.isBlank()) return false;
            String current = resolveAliasTarget();
            try {
                switchAlias(previousIndex);
            activeIndex = previousIndex;
            previousIndex = current;
            indexReady.set(true);
            healthy = true;
            return true;
        } catch (RuntimeException ex) {
            healthy = false;
            return false;
        }
    }

    @Override
    public void replaceDocument(String id, List<AgentKnowledgeStore.Document> documents) {
        if (id == null || id.isBlank()) return;
        String normalized = id.trim();
        List<AgentKnowledgeStore.Document> safe = documents == null ? List.of() : List.copyOf(documents);
        try {
            int dimensions = safe.stream().map(AgentKnowledgeStore.Document::embedding)
                    .filter(java.util.Objects::nonNull).mapToInt(value -> value.length).findFirst().orElse(64);
            ensureIndex(dimensions);
            for (AgentKnowledgeStore.Document document : safe) write(document, false);
            Map<String, Object> identity = Map.of("bool", Map.of("should", List.of(
                    Map.of("term", Map.of("id", normalized)),
                    Map.of("prefix", Map.of("id", normalized + "#"))),
                    "minimum_should_match", 1));
            Map<String, Object> query = safe.isEmpty()
                    ? identity
                    : Map.of("bool", Map.of("must", List.of(identity), "must_not", List.of(
                            Map.of("terms", Map.of("id", safe.stream().map(AgentKnowledgeStore.Document::id).toList())))));
            deleteByQuery(query);
            healthy = true;
        } catch (RuntimeException ex) {
            healthy = false;
            throw ex;
        }
    }

    @Override
    public List<AgentKnowledgeStore.Document> search(String query, int limit, String source) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            int safeLimit = Math.max(1, Math.min(20, limit));
            java.util.Optional<double[]> queryVector = embeddingClient == null
                    ? java.util.Optional.empty() : embeddingClient.embed(query == null ? "" : query);
            if (queryVector.isPresent()) {
                Map<String, Object> knn = new LinkedHashMap<>();
                knn.put("field", "embedding");
                knn.put("query_vector", boxed(queryVector.get()));
                knn.put("k", safeLimit);
                knn.put("num_candidates", Math.max(safeLimit * 4, 20));
                if (source != null && !source.isBlank()) {
                    knn.put("filter", Map.of("term", Map.of("source", source.trim())));
                }
                body.put("knn", knn);
            } else {
                Map<String, Object> bool = new LinkedHashMap<>();
                List<Map<String, Object>> must = new ArrayList<>();
                must.add(Map.of("match", Map.of("text", query == null ? "" : query)));
                if (source != null && !source.isBlank()) must.add(Map.of("term", Map.of("source", source.trim())));
                bool.put("must", must);
                body.put("query", Map.of("bool", bool));
            }
            body.put("size", safeLimit);
            JsonNode response = client.post().uri("/" + indexName() + "/_search")
                    .body(body).retrieve().body(JsonNode.class);
            healthy = true;
            List<AgentKnowledgeStore.Document> result = new ArrayList<>();
            JsonNode hits = response == null ? null : response.path("hits").path("hits");
            if (hits != null && hits.isArray()) for (JsonNode hit : hits) {
                JsonNode value = hit.path("_source");
                if (!value.isObject()) continue;
                result.add(objectMapper.treeToValue(value, AgentKnowledgeStore.Document.class));
            }
            return result;
        } catch (Exception ex) {
            healthy = false;
            String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            // A mapping mismatch is a correctness failure, not a transient
            // provider outage. Returning an empty result would silently hide
            // an index that can no longer represent the configured vectors.
            if (message.contains("dimension") || message.contains("维度")) {
                throw new IllegalStateException(ex.getMessage(), ex);
            }
            return List.of();
        }
    }

    @Override public boolean available() { return healthy; }
    @Override public String name() { return "elasticsearch-vector"; }

    @Override
    public Map<String, Object> health() {
        try {
            JsonNode cluster = client.get().uri("/_cluster/health").retrieve().body(JsonNode.class);
            healthy = cluster != null && !cluster.path("status").asText("").isBlank();
            return Map.of("name", name(), "available", healthy,
                    "clusterStatus", cluster == null ? "unknown" : cluster.path("status").asText("unknown"),
                    "index", indexName());
        } catch (RuntimeException ex) {
            healthy = false;
            return Map.of("name", name(), "available", false, "reason", "unreachable",
                    "index", indexName());
        }
    }

    private synchronized void ensureIndex(int dimensions) {
        int safeDimensions = Math.max(1, Math.min(dimensions, 4096));
        if (indexReady.get()) return;
        try {
            try {
                JsonNode existing = client.get().uri("/" + indexName()).retrieve().body(JsonNode.class);
                int mapped = mappingDimensions(existing);
                if (mapped > 0 && mapped != safeDimensions) {
                    throw new IllegalStateException("Elasticsearch embedding dimensions mismatch: index=" + mapped + ", document=" + safeDimensions);
                }
                indexReady.set(true);
                return;
            } catch (HttpClientErrorException.NotFound ignored) {
                // Create the index below when this is the first writer.
            }
            Map<String, Object> propertiesMap = new LinkedHashMap<>();
            propertiesMap.put("id", Map.of("type", "keyword"));
            propertiesMap.put("source", Map.of("type", "keyword"));
            propertiesMap.put("text", Map.of("type", "text"));
            propertiesMap.put("embedding", Map.of("type", "dense_vector", "dims", safeDimensions, "index", true, "similarity", "cosine"));
            client.put().uri("/" + indexName())
                    .body(Map.of("mappings", Map.of("properties", propertiesMap)))
                    .retrieve().toBodilessEntity();
            indexReady.set(true);
        } catch (HttpClientErrorException.Conflict ignored) {
            // Another instance won creation. Inspect its mapping now so a
            // concurrent writer cannot hide an embedding dimension mismatch.
            verifyExistingMapping(safeDimensions);
        } catch (RuntimeException ex) {
            indexReady.set(false);
            throw ex;
        }
    }

    private void verifyExistingMapping(int expectedDimensions) {
        JsonNode existing = client.get().uri("/" + indexName()).retrieve().body(JsonNode.class);
        int mapped = mappingDimensions(existing);
        if (mapped > 0 && mapped != expectedDimensions) {
            throw new IllegalStateException("Elasticsearch embedding dimensions mismatch: index=" + mapped
                    + ", document=" + expectedDimensions);
        }
        indexReady.set(true);
    }

    private String baseIndexName() { return AgentRequestContext.indexName(properties.vectorIndexName()); }
    private String aliasName() { return baseIndexName() + "-alias"; }
    private String indexName() { return activeIndex == null ? baseIndexName() : aliasName(); }

    private void write(AgentKnowledgeStore.Document document, boolean visibleBeforeReturn) {
        ensureIndex(document.embedding() == null ? 64 : document.embedding().length);
        writeTo(indexName(), document, visibleBeforeReturn);
    }

    private void writeTo(String targetIndex, AgentKnowledgeStore.Document document, boolean visibleBeforeReturn) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", document.id()); source.put("source", document.source());
        source.put("text", document.text()); source.put("embedding", document.embedding());
        source.put("metadata", document.metadata());
        String suffix = visibleBeforeReturn ? "?refresh=wait_for" : "";
        client.put().uri("/" + targetIndex + "/_doc/" + encode(document.id()) + suffix)
                .body(source).retrieve().toBodilessEntity();
    }

    private void ensureIndexAt(String target, int dimensions) {
        int safeDimensions = Math.max(1, Math.min(dimensions, 4096));
        try {
            JsonNode existing = client.get().uri("/" + target).retrieve().body(JsonNode.class);
            int mapped = mappingDimensions(existing);
            if (mapped > 0 && mapped != safeDimensions) {
                throw new IllegalStateException("Elasticsearch embedding dimensions mismatch: index=" + mapped + ", document=" + safeDimensions);
            }
            return;
        } catch (HttpClientErrorException.NotFound ignored) { }
        Map<String, Object> propertiesMap = new LinkedHashMap<>();
        propertiesMap.put("id", Map.of("type", "keyword"));
        propertiesMap.put("source", Map.of("type", "keyword"));
        propertiesMap.put("text", Map.of("type", "text"));
        propertiesMap.put("embedding", Map.of("type", "dense_vector", "dims", safeDimensions, "index", true, "similarity", "cosine"));
        try {
            client.put().uri("/" + target).body(Map.of("mappings", Map.of("properties", propertiesMap)))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.Conflict ignored) {
            JsonNode existing = client.get().uri("/" + target).retrieve().body(JsonNode.class);
            int mapped = mappingDimensions(existing);
            if (mapped > 0 && mapped != safeDimensions) throw new IllegalStateException("Elasticsearch embedding dimensions mismatch");
        }
    }

    private synchronized void switchAlias(String target) {
        String alias = aliasName();
        String old = resolveAliasTarget();
        List<Map<String, Object>> actions = new ArrayList<>();
        actions.add(Map.of("add", Map.of("index", target, "alias", alias)));
        if (old != null && !old.equals(target)) actions.add(Map.of("remove", Map.of("index", old, "alias", alias)));
        client.post().uri("/_aliases").body(Map.of("actions", actions)).retrieve().toBodilessEntity();
    }

    private String resolveAliasTarget() {
        try {
            JsonNode value = client.get().uri("/_alias/" + aliasName()).retrieve().body(JsonNode.class);
            if (value != null && value.fieldNames().hasNext()) return value.fieldNames().next();
        } catch (HttpClientErrorException.NotFound ignored) { }
        return null;
    }

    private static int mappingDimensions(JsonNode value) {
        if (value == null || !value.isObject()) return -1;
        var fields = value.fields();
        while (fields.hasNext()) {
            JsonNode mapping = fields.next().getValue().path("mappings").path("properties").path("embedding").path("dims");
            if (mapping.isInt() || mapping.isLong()) return mapping.asInt(-1);
        }
        return -1;
    }

    /**
     * Elasticsearch is near-real-time by default.  Every public delete or
     * source replacement must therefore wait for a refresh, otherwise a
     * caller can receive a successful response while stale knowledge remains
     * visible to the next Agent retrieval. Elasticsearch accepts only a
     * boolean refresh value for delete-by-query, unlike the document index API.
     */
    private void deleteByQuery(Map<String, Object> query) {
        JsonNode response = client.post().uri("/" + indexName()
                        + "/_delete_by_query?conflicts=proceed&refresh=true")
                .body(Map.of("query", query, "conflicts", "proceed"))
                .retrieve().body(JsonNode.class);
        if (response != null && (response.path("timed_out").asBoolean(false)
                || (response.path("failures").isArray() && !response.path("failures").isEmpty()))) {
            throw new IllegalStateException("Elasticsearch delete-by-query did not complete");
        }
    }

    private static List<Double> boxed(double[] values) {
        List<Double> result = new ArrayList<>(values.length);
        for (double value : values) result.add(value);
        return result;
    }

    private static String encode(String value) { return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8); }
}

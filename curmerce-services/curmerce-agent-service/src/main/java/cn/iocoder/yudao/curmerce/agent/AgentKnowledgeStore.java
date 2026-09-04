package cn.iocoder.yudao.curmerce.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;
import java.util.UUID;

/**
 * Small vector-store adapter used by the service until Elasticsearch/OpenSearch
 * vector fields are enabled. Documents and the interface are intentionally
 * provider-neutral, so a persistent vector backend can be introduced without
 * changing Agent retrieval or tool contracts.
 */
@Component
public class AgentKnowledgeStore {
    private static final String REDIS_KEY = "curmerce:agent:knowledge:v1:documents";
    private static final String SOURCE_VERSION_KEY = "curmerce:agent:knowledge:v1:source-versions";
    private static final String PENDING_EXTERNAL_DELETE_KEY = "curmerce:agent:knowledge:v1:pending-deletes";
    private static final String PENDING_EXTERNAL_UPSERT_KEY = "curmerce:agent:knowledge:v1:pending-upserts";
    private static final String SOURCE_REBUILD_LOCK = "curmerce:agent:knowledge:v1:source-rebuild-lock";
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<Long> RELEASE_SOURCE_LOCK =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                    "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end", Long.class);
    private final Map<String, Document> documents = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AgentEmbeddingClient embeddingClient;
    private final AgentVectorBackend externalBackend;
    private final Map<String, Long> sourceVersions = new ConcurrentHashMap<>();
    private final Set<String> pendingExternalDeletes = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingExternalUpserts = ConcurrentHashMap.newKeySet();
    private final MeterRegistry metrics;
    private volatile int embeddingDimensions;

    public AgentKnowledgeStore() {
        this((StringRedisTemplate) null, (ObjectMapper) null, (AgentEmbeddingClient) null, null, null);
    }

    @Autowired
    public AgentKnowledgeStore(ObjectProvider<StringRedisTemplate> provider, ObjectMapper objectMapper,
                               ObjectProvider<AgentEmbeddingClient> embeddingProvider,
                               ObjectProvider<AgentVectorBackend> backendProvider,
                               ObjectProvider<MeterRegistry> metricsProvider) {
        this(provider.getIfAvailable(), objectMapper, embeddingProvider.getIfAvailable(), backendProvider.getIfAvailable(),
                metricsProvider.getIfAvailable());
    }

    AgentKnowledgeStore(StringRedisTemplate redis, ObjectMapper objectMapper, AgentEmbeddingClient embeddingClient) {
        this(redis, objectMapper, embeddingClient, null, null);
    }

    AgentKnowledgeStore(StringRedisTemplate redis, ObjectMapper objectMapper, AgentEmbeddingClient embeddingClient,
                        AgentVectorBackend externalBackend) {
        this(redis, objectMapper, embeddingClient, externalBackend, null);
    }

    AgentKnowledgeStore(StringRedisTemplate redis, ObjectMapper objectMapper, AgentEmbeddingClient embeddingClient,
                        AgentVectorBackend externalBackend, MeterRegistry metrics) {
        this.redis = redis; this.objectMapper = objectMapper; this.embeddingClient = embeddingClient;
        this.externalBackend = externalBackend; this.metrics = metrics;
        if (metrics != null) {
            io.micrometer.core.instrument.Gauge.builder("curmerce.agent.knowledge.external.pending.upserts", this,
                    AgentKnowledgeStore::pendingExternalUpserts).register(metrics);
            io.micrometer.core.instrument.Gauge.builder("curmerce.agent.knowledge.external.pending.deletes", this,
                    AgentKnowledgeStore::pendingExternalDeletes).register(metrics);
        }
    }

    @PostConstruct
    void restorePendingExternalOperations() {
        if (redis == null || externalBackend == null) return;
        try {
            Set<String> deletes = redis.opsForSet().members(key(PENDING_EXTERNAL_DELETE_KEY));
            if (deletes != null) pendingExternalDeletes.addAll(deletes);
            Set<String> upserts = redis.opsForSet().members(key(PENDING_EXTERNAL_UPSERT_KEY));
            if (upserts != null) pendingExternalUpserts.addAll(upserts);
        } catch (RuntimeException ignored) {
            // The Redis mirror is optional during startup. Later reconciliations
            // re-read the durable sets once it becomes reachable.
        }
    }

    public void upsert(String id, String source, String text, JsonNode metadata) {
        if (id == null || id.isBlank() || text == null || text.isBlank()) return;
        String normalizedSource = source == null || source.isBlank() ? "unknown" : source.trim();
        String normalizedId = id.trim();
        // A new write supersedes a previously failed delete for the same
        // logical document. Otherwise the delete repair could remove the new
        // version after this upsert has succeeded.
        clearPendingExternalDelete(normalizedId);
        Document document = new Document(normalizedId, normalizedSource, text.trim(), checkedVector(vector(text)), metadata);
        documents.put(document.id(), document);
        persist(document);
        if (externalBackend != null) {
            try {
                externalBackend.upsert(document);
                clearPendingExternalUpsert(document.id());
            }
            catch (RuntimeException ex) {
                // A provider outage may use the local mirror, but a mapping
                // mismatch would make the projection semantically unsafe and
                // must be visible to the caller.
                String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                if (message.contains("dimension") || message.contains("维度")) throw ex;
                markPendingExternalUpsert(document.id());
            }
        }
    }

    /** Store bounded overlapping chunks so long posts cannot crowd out the context window. */
    public int upsertChunked(String id, String source, String text, JsonNode metadata) {
        if (id == null || id.isBlank() || text == null || text.isBlank()) return 0;
        return replaceDocument(id, source, text, metadata);
    }

    /**
     * Replaces exactly one logical knowledge document. This differs from
     * replaceSource: product and post events must never clear unrelated
     * documents, while shortening a long text must remove its obsolete chunks.
     */
    public synchronized int replaceDocument(String id, String source, String text, JsonNode metadata) {
        if (id == null || id.isBlank() || text == null || text.isBlank()) return 0;
        String normalizedId = id.trim();
        String normalizedSource = source == null || source.isBlank() ? "unknown" : source.trim();
        String value = text.trim();
        clearPendingExternalDelete(normalizedId);
        int chunkSize = 1600;
        int overlap = 120;
        List<Document> staged = new ArrayList<>();
        for (int start = 0; start < value.length(); ) {
            int end = Math.min(value.length(), start + chunkSize);
            String chunkId = value.length() <= chunkSize ? normalizedId : normalizedId + "#" + staged.size();
            staged.add(new Document(chunkId, normalizedSource, value.substring(start, end),
                    checkedVector(vector(value.substring(start, end))), metadata));
            if (end == value.length()) break;
            start = end - overlap;
        }
        boolean externalApplied = externalBackend == null;
        if (externalBackend != null) {
            try {
                externalBackend.replaceDocument(normalizedId, staged);
                externalApplied = true;
            } catch (RuntimeException ex) {
                String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                if (message.contains("dimension") || message.contains("维度")) throw ex;
                markPendingExternalUpsert(normalizedId);
            }
        }
        allDocuments().stream().map(Document::id)
                .filter(existing -> existing.equals(normalizedId) || existing.startsWith(normalizedId + "#"))
                .forEach(this::removeLocal);
        for (Document document : staged) {
            documents.put(document.id(), document);
            persist(document);
        }
        if (externalApplied) clearPendingExternalUpsert(normalizedId);
        else markPendingExternalUpsert(normalizedId);
        return staged.size();
    }

    public List<Document> search(String query, int limit) {
        return search(query, limit, null);
    }

    public List<Document> search(String query, int limit, String source) {
        if (externalBackend != null) {
            List<Document> external = externalBackend.search(query, limit, source);
            if (!external.isEmpty() || externalBackend.available()) return external;
        }
        double[] q = vector(query);
        return allDocuments().stream()
                .filter(document -> source == null || source.isBlank() || source.equals(document.source()))
                .map(document -> Map.entry(document, cosine(q, document.embedding())))
                .sorted(Map.Entry.<Document, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(Math.max(1, Math.min(20, limit)))
                .map(Map.Entry::getKey).toList();
    }

    public int size() { return allDocuments().size(); }
    public String backendName() {
        return externalBackend != null ? externalBackend.name() : (persistent() ? "redis-vector-adapter" : "local-vector-adapter");
    }
    public Map<String, Integer> sourceCounts() {
        return allDocuments().stream().collect(java.util.stream.Collectors.groupingBy(Document::source,
                java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.counting(), Long::intValue)));
    }
    public Map<String, Long> sourceVersions() {
        Map<String, Long> result = new java.util.LinkedHashMap<>(sourceVersions);
        if (redis != null) try {
            redis.opsForHash().entries(key(SOURCE_VERSION_KEY)).forEach((key, value) -> result.put(String.valueOf(key), longValue(value)));
        } catch (RuntimeException ignored) { }
        return Map.copyOf(result);
    }
    public synchronized long nextSourceVersion(String source) {
        String normalized = source == null || source.isBlank() ? "unknown" : source.trim();
        if (redis != null) try {
            Long value = redis.opsForHash().increment(key(SOURCE_VERSION_KEY), normalized, 1L);
            long version = value == null ? 1L : value;
            sourceVersions.put(normalized, version);
            return version;
        } catch (RuntimeException ignored) { }
        long version = sourceVersions.getOrDefault(normalized, 0L) + 1L;
        sourceVersions.put(normalized, version);
        return version;
    }
    /**
     * Redis is the local durable mirror.  An external vector backend is a
     * projection, not a reason to dereference a missing Redis client.
     */
    public boolean persistent() { return redis != null && objectMapper != null; }
    public void remove(String id) {
        if (id == null || id.isBlank()) return;
        String normalizedId = id.trim();
        clearPendingExternalUpsert(normalizedId);
        documents.keySet().removeIf(value -> value.equals(normalizedId) || value.startsWith(normalizedId + "#"));
        if (redis != null) try {
            Map<Object, Object> values = redis.opsForHash().entries(key(REDIS_KEY));
            values.keySet().stream().map(String::valueOf)
                    .filter(value -> value.equals(normalizedId) || value.startsWith(normalizedId + "#"))
                    .forEach(value -> redis.opsForHash().delete(key(REDIS_KEY), value));
        } catch (RuntimeException ignored) { }
        if (externalBackend != null) {
            pendingExternalDeletes.add(normalizedId);
            if (redis != null) try { redis.opsForSet().add(key(PENDING_EXTERNAL_DELETE_KEY), normalizedId); }
            catch (RuntimeException ignored) { }
            try {
                if (externalBackend.remove(normalizedId)) clearPendingExternalDelete(normalizedId);
            } catch (RuntimeException ignored) {
                // Keep the id in the durable repair set until the projection
                // provider confirms the delete.
            }
        }
    }
    public void clear() {
        documents.clear();
        embeddingDimensions = 0;
        pendingExternalDeletes.clear();
        pendingExternalUpserts.clear();
        if (redis != null) try { redis.delete(key(REDIS_KEY)); } catch (RuntimeException ignored) { }
        if (redis != null) try { redis.delete(key(PENDING_EXTERNAL_DELETE_KEY)); } catch (RuntimeException ignored) { }
        if (redis != null) try { redis.delete(key(PENDING_EXTERNAL_UPSERT_KEY)); } catch (RuntimeException ignored) { }
        if (externalBackend != null) {
            try { externalBackend.clearAll(); } catch (RuntimeException ignored) { }
        }
    }

    public boolean backendAvailable() { return externalBackend == null || externalBackend.available(); }
    public Map<String, Object> backendHealth() {
        if (externalBackend == null) return Map.of("name", backendName(), "available", true);
        try { return externalBackend.health(); }
        catch (RuntimeException ex) { return Map.of("name", externalBackend.name(), "available", false, "reason", "health-check-failed"); }
    }
    public boolean embeddingEnabled() { return embeddingClient != null && embeddingClient.enabled(); }
    public int embeddingDimensions() { return embeddingDimensions; }
    public int pendingExternalDeletes() { return pendingExternalDeleteIds().size(); }
    public int pendingExternalUpserts() { return pendingExternalUpsertIds().size(); }

    /** Retries external projection deletes without exposing document contents. */
    public int reconcileExternalDeletes() {
        if (externalBackend == null) return 0;
        int applied = 0;
        for (String id : pendingExternalDeleteIds()) {
            try {
                if (externalBackend.remove(id)) {
                    clearPendingExternalDelete(id);
                    applied++;
                }
            } catch (RuntimeException ignored) { }
        }
        return applied;
    }

    /**
     * Replays writes that reached the local durable mirror while the external
     * vector projection was unavailable. A delete always wins over a stale
     * write, so removed documents are never resurrected after a restart.
     */
    public int reconcileExternalUpserts() {
        if (externalBackend == null) return 0;
        Map<String, Document> known = allDocuments().stream()
                .collect(java.util.stream.Collectors.toMap(Document::id, document -> document, (left, right) -> right));
        int applied = 0;
        for (String id : pendingExternalUpsertIds()) {
            if (pendingExternalDeleteIds().contains(id)) {
                clearPendingExternalUpsert(id);
                continue;
            }
            List<Document> replacement = known.values().stream()
                    .filter(document -> document.id().equals(id) || document.id().startsWith(id + "#"))
                    .sorted(Comparator.comparing(Document::id)).toList();
            if (replacement.isEmpty()) {
                clearPendingExternalUpsert(id);
                continue;
            }
            try {
                if (replacement.size() == 1 && replacement.get(0).id().equals(id)) {
                    externalBackend.upsert(replacement.get(0));
                } else {
                    externalBackend.replaceDocument(id, replacement);
                }
                clearPendingExternalUpsert(id);
                applied++;
            } catch (RuntimeException ex) {
                String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
                if (message.contains("dimension") || message.contains("维度")) throw ex;
            }
        }
        return applied;
    }

    /** Runs write repair before delete repair, with delete conflict protection in each replay. */
    public int reconcileExternalOperations() {
        return reconcileExternalUpserts() + reconcileExternalDeletes();
    }

    private Set<String> pendingExternalDeleteIds() {
        Set<String> result = ConcurrentHashMap.newKeySet();
        result.addAll(pendingExternalDeletes);
        if (redis != null) try {
            Set<String> remote = redis.opsForSet().members(key(PENDING_EXTERNAL_DELETE_KEY));
            if (remote != null) result.addAll(remote);
        } catch (RuntimeException ignored) { }
        return Set.copyOf(result);
    }

    private void clearPendingExternalDelete(String id) {
        pendingExternalDeletes.remove(id);
        if (redis != null) try { redis.opsForSet().remove(key(PENDING_EXTERNAL_DELETE_KEY), id); }
        catch (RuntimeException ignored) { }
    }

    private Set<String> pendingExternalUpsertIds() {
        Set<String> result = ConcurrentHashMap.newKeySet();
        result.addAll(pendingExternalUpserts);
        if (redis != null) try {
            Set<String> remote = redis.opsForSet().members(key(PENDING_EXTERNAL_UPSERT_KEY));
            if (remote != null) result.addAll(remote);
        } catch (RuntimeException ignored) { }
        return Set.copyOf(result);
    }

    private void markPendingExternalUpsert(String id) {
        if (id == null || id.isBlank()) return;
        String normalized = id.trim();
        pendingExternalUpserts.add(normalized);
        if (redis != null) try { redis.opsForSet().add(key(PENDING_EXTERNAL_UPSERT_KEY), normalized); }
        catch (RuntimeException ignored) { }
    }

    private void clearPendingExternalUpsert(String id) {
        if (id == null || id.isBlank()) return;
        String normalized = id.trim();
        pendingExternalUpserts.removeIf(value -> value.equals(normalized) || value.startsWith(normalized + "#"));
        if (redis != null) try {
            Set<String> ids = redis.opsForSet().members(key(PENDING_EXTERNAL_UPSERT_KEY));
            if (ids != null) ids.stream().filter(value -> value.equals(normalized) || value.startsWith(normalized + "#"))
                    .forEach(value -> redis.opsForSet().remove(key(PENDING_EXTERNAL_UPSERT_KEY), value));
        } catch (RuntimeException ignored) { }
    }

    private String key(String base) { return AgentRequestContext.key(base); }

    /** Replace one source projection after an event-driven rebuild. */
    public synchronized int replaceSource(String source, List<SourceDocument> replacement) {
        String normalized = source == null || source.isBlank() ? "unknown" : source.trim();
        return replaceSource(normalized, replacement, nextSourceVersion(normalized));
    }

    /** Applies only the newest submitted source version, preventing stale workers from overwriting newer data. */
    public synchronized int replaceSource(String source, List<SourceDocument> replacement, long version) {
        SourceRebuildLock lock = acquireSourceRebuildLock(source);
        try {
            return replaceSourceLocked(source, replacement, version);
        } finally {
            releaseSourceRebuildLock(lock);
        }
    }

    /**
     * The JVM-level synchronized guard is insufficient once two Agent
     * instances project the same source into one Elasticsearch index.  A
     * short Redis lease serializes the external write-before-delete cutover;
     * the version check is repeated after acquiring it so an older worker
     * cannot replace a newer projection that raced while it was queued.
     */
    private int replaceSourceLocked(String source, List<SourceDocument> replacement, long version) {
        String normalized = source == null || source.isBlank() ? "unknown" : source.trim();
        long current = sourceVersions.getOrDefault(normalized, 0L);
        if (redis != null) try { current = Math.max(current, longValue(redis.opsForHash().get(key(SOURCE_VERSION_KEY), normalized))); }
        catch (RuntimeException ignored) { }
        if (version < current) return 0;
        List<SourceDocument> safeReplacement = replacement == null ? List.of() : List.copyOf(replacement);
        List<Document> staged = new ArrayList<>();
        for (SourceDocument document : safeReplacement) {
            if (document == null || document.id() == null || document.id().isBlank()
                    || document.text() == null || document.text().isBlank()) continue;
            String value = document.text().trim();
            int chunkSize = 1600;
            int overlap = 120;
            if (value.length() <= chunkSize) {
                staged.add(new Document(document.id().trim(), normalized, value, vector(value), document.metadata()));
                continue;
            }
            int chunk = 0;
            for (int start = 0; start < value.length();) {
                int end = Math.min(value.length(), start + chunkSize);
                String chunkText = value.substring(start, end);
                staged.add(new Document(document.id().trim() + "#" + chunk++, normalized,
                        chunkText, vector(chunkText), document.metadata()));
                if (end == value.length()) break;
                start = end - overlap;
            }
        }
        // Validate the complete replacement before deleting the previous
        // projection. A provider/dimension error must leave the old index
        // searchable and retryable.
        staged.forEach(document -> checkedVector(document.embedding()));
        // Let the external provider complete its write-before-delete cutover
        // first; a failed rebuild must leave the previous index searchable.
        if (externalBackend != null) externalBackend.replaceSource(normalized, staged);
        // The provider has already cut over. Remove only the local/Redis
        // mirror here; calling remove() would delete newly written ES rows
        // when an ID is reused during a rebuild.
        allDocuments().stream().filter(document -> normalized.equals(document.source()))
                .map(Document::id).forEach(this::removeLocal);
        for (Document document : staged) {
            documents.put(document.id(), document);
            persist(document);
        }
        // Advance the version only after the complete replacement has been
        // accepted. A failed provider write must remain retryable instead of
        // making the old projection appear current.
        sourceVersions.put(normalized, version);
        if (redis != null) try {
            redis.opsForHash().put(key(SOURCE_VERSION_KEY), normalized, String.valueOf(version));
        } catch (RuntimeException ignored) { }
        return staged.size();
    }

    private SourceRebuildLock acquireSourceRebuildLock(String source) {
        // Local/Redis-only projections already have the synchronized guard;
        // the distributed lease is needed for the shared external index.
        if (redis == null || externalBackend == null) return null;
        String normalized = source == null || source.isBlank() ? "unknown" : source.trim();
        String key = AgentRequestContext.key(SOURCE_REBUILD_LOCK + ":" + AgentPrincipalHasher.hash(normalized));
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(key, token, Duration.ofSeconds(300));
            if (!Boolean.TRUE.equals(acquired)) {
                throw new IllegalStateException("Agent knowledge source rebuild is busy");
            }
            return new SourceRebuildLock(key, token);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Agent knowledge source rebuild lock is unavailable", ex);
        }
    }

    private void releaseSourceRebuildLock(SourceRebuildLock lock) {
        if (lock == null || redis == null) return;
        try { redis.execute(RELEASE_SOURCE_LOCK, List.of(lock.key()), lock.token()); }
        catch (RuntimeException ignored) { /* lease expiry remains the safety net */ }
    }

    private void persist(Document document) {
        if (!persistent()) return;
        try { redis.opsForHash().put(key(REDIS_KEY), document.id(), objectMapper.writeValueAsString(document)); }
        catch (Exception ignored) { /* local copy remains available while Redis recovers */ }
    }

    private void removeLocal(String id) {
        clearPendingExternalUpsert(id);
        documents.keySet().removeIf(value -> value.equals(id) || value.startsWith(id + "#"));
        if (redis != null) try {
            Map<Object, Object> values = redis.opsForHash().entries(key(REDIS_KEY));
            values.keySet().stream().map(String::valueOf)
                    .filter(value -> value.equals(id) || value.startsWith(id + "#"))
                    .forEach(value -> redis.opsForHash().delete(key(REDIS_KEY), value));
        } catch (RuntimeException ignored) { }
    }

    private double[] vector(String text) {
        if (embeddingClient != null && embeddingClient.enabled()) {
            java.util.Optional<double[]> remote;
            try {
                remote = embeddingClient.embed(text);
            } catch (RuntimeException ex) {
                throw new IllegalStateException("Embedding 服务不可用，知识写入未完成", ex);
            }
            if (remote.isEmpty()) {
                throw new IllegalStateException("Embedding 服务未返回有效向量，知识写入未完成");
            }
            double[] value = remote.get();
            if (embeddingDimensions > 0 && embeddingDimensions != value.length) {
                throw new IllegalArgumentException("知识向量维度不一致: expected="
                        + embeddingDimensions + ", actual=" + value.length);
            }
            return value;
        }
        return embed(text);
    }

    private synchronized double[] checkedVector(double[] value) {
        if (value == null || value.length == 0) throw new IllegalArgumentException("知识向量不能为空");
        if (embeddingDimensions == 0) embeddingDimensions = value.length;
        if (embeddingDimensions != value.length) {
            throw new IllegalArgumentException("知识向量维度不一致: expected=" + embeddingDimensions + ", actual=" + value.length);
        }
        return value;
    }

    private List<Document> allDocuments() {
        Map<String, Document> merged = new ConcurrentHashMap<>(documents);
        // A running deployment may temporarily contain a projection written
        // by a different embedding model (for example, a local 64-dimensional
        // fallback next to an 8-dimensional test provider). Establish the
        // expected dimension from the current in-memory projection first so
        // stale Redis rows cannot redefine the active model's dimension.
        if (embeddingDimensions == 0 && !merged.isEmpty()) {
            merged.values().stream().findFirst().ifPresent(document -> {
                if (document.embedding() != null && document.embedding().length > 0) {
                    embeddingDimensions = document.embedding().length;
                }
            });
        }
        if (persistent()) {
            Map<Object, Object> values;
            try {
                values = redis.opsForHash().entries(key(REDIS_KEY));
            } catch (RuntimeException ignored) {
                values = Map.of();
            }
            for (Object value : values.values()) {
                try {
                    Document document = objectMapper.readValue(String.valueOf(value), Document.class);
                    checkedVector(document.embedding());
                    merged.put(document.id(), document);
                } catch (IllegalArgumentException ex) {
                    // Reads are allowed to degrade around stale rows from a
                    // previous embedding model. Writes still fail fast in
                    // checkedVector(), and the projection can be repaired by
                    // an explicit rebuild; a single incompatible row must
                    // not turn a read-only Agent request into HTTP 500.
                    if (ex.getMessage() == null || !ex.getMessage().contains("知识向量维度不一致")) {
                        throw ex;
                    }
                } catch (Exception ignored) { }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static double[] embed(String text) {
        double[] vector = new double[64];
        for (int i = 0; i < text.length(); i++) vector[(text.charAt(i) * 31 + i) & 63] += 1D;
        double norm = 0D; for (double value : vector) norm += value * value;
        norm = Math.sqrt(norm); if (norm == 0D) return vector;
        for (int i = 0; i < vector.length; i++) vector[i] /= norm;
        return vector;
    }
    private static long longValue(Object value) {
        if (value == null) return 0L;
        try { return Long.parseLong(String.valueOf(value)); } catch (RuntimeException ex) { return 0L; }
    }

    private static double cosine(double[] left, double[] right) {
        double result = 0D;
        int length = Math.min(left.length, right.length);
        for (int i = 0; i < length; i++) result += left[i] * right[i];
        return result;
    }

    public record SourceDocument(String id, String text, JsonNode metadata) { }
    public record Document(String id, String source, String text, double[] embedding, JsonNode metadata) { }
    private record SourceRebuildLock(String key, String token) { }
}

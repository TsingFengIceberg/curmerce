package cn.iocoder.yudao.curmerce.agent;

import java.util.List;
import java.util.Map;

/** Provider-neutral vector index boundary for Agent knowledge documents. */
public interface AgentVectorBackend {
    void upsert(AgentKnowledgeStore.Document document);
    /** Returns false when the provider could not durably apply the delete. */
    default boolean remove(String id) { return true; }
    void clearSource(String source);
    /** Clears the complete projection when the local mirror is reset. */
    default void clearAll() { }
    /**
     * Replaces one source without deleting the current projection before the
     * new documents have been accepted. Implementations may provide a more
     * atomic provider-specific cutover; the default is write-before-delete so
     * a transient write failure leaves old data usable.
     */
    default void replaceSource(String source, List<AgentKnowledgeStore.Document> documents) {
        if (documents != null) documents.forEach(this::upsert);
        clearSource(source);
        if (documents != null) documents.forEach(this::upsert);
    }
    /**
     * Replaces one logical source document, including its deterministic
     * chunks. Implementations with a remote index should write the replacement
     * before removing chunks no longer present in the new document.
     */
    default void replaceDocument(String id, List<AgentKnowledgeStore.Document> documents) {
        if (documents != null) documents.forEach(this::upsert);
    }
    /**
     * Rebuilds the complete tenant projection into a new versioned index and
     * cuts the stable alias over only after every document has been accepted.
     * Implementations without alias support retain the safe in-place default.
     */
    default void rebuildVersioned(List<AgentKnowledgeStore.Document> documents) {
        if (documents != null) documents.forEach(this::upsert);
    }
    /** Rolls an alias back to the previously active version when supported. */
    default boolean rollbackVersioned() { return false; }
    List<AgentKnowledgeStore.Document> search(String query, int limit, String source);
    boolean available();
    String name();

    default Map<String, Object> health() {
        return Map.of("name", name(), "available", available());
    }
}

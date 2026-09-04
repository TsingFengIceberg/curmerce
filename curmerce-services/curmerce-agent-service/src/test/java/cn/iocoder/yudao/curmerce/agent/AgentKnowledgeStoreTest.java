package cn.iocoder.yudao.curmerce.agent;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doReturn;

class AgentKnowledgeStoreTest {
    @Test
    void upsertAndSemanticSearchReturnRelevantDocument() {
        AgentKnowledgeStore store = new AgentKnowledgeStore();
        store.upsert("p1", "product", "手冲咖啡磨豆机 不锈钢", JsonNodeFactory.instance.objectNode());
        store.upsert("p2", "product", "蓝牙运动耳机", JsonNodeFactory.instance.objectNode());
        assertEquals(2, store.size());
        assertTrue(store.search("咖啡磨豆", 1).get(0).id().equals("p1"));
    }

    @Test
    void sourceProjectionCanBeFilteredAndRebuilt() {
        AgentKnowledgeStore store = new AgentKnowledgeStore();
        store.upsert("p1", "product", "咖啡磨豆机", JsonNodeFactory.instance.objectNode());
        store.upsert("c1", "community", "咖啡体验", JsonNodeFactory.instance.objectNode());
        assertEquals(1, store.search("咖啡", 5, "product").size());
        assertEquals(1, store.replaceSource("product", java.util.List.of(
                new AgentKnowledgeStore.SourceDocument("p2", "新商品", JsonNodeFactory.instance.objectNode()))));
        assertTrue(store.search("咖啡", 5, "product").stream().noneMatch(document -> document.id().equals("p1")));
        assertEquals(1, store.sourceCounts().get("product"));
    }

    @Test
    void replacingOneDocumentRemovesObsoleteLongTextChunksWithoutTouchingOthers() {
        AgentKnowledgeStore store = new AgentKnowledgeStore();
        store.replaceDocument("community:7", "community", "a".repeat(3_500), JsonNodeFactory.instance.objectNode());
        store.upsert("product:3", "product", "咖啡磨豆机", JsonNodeFactory.instance.objectNode());

        store.replaceDocument("community:7", "community", "缩短后的帖子", JsonNodeFactory.instance.objectNode());

        assertEquals(2, store.size());
        assertEquals(1, store.search("缩短", 10, "community").size());
        assertTrue(store.search("咖啡", 10, "product").stream().anyMatch(document -> document.id().equals("product:3")));
    }

    @Test
    void embeddingDimensionChangesAreRejectedInsteadOfSilentlyFallingBack() {
        AgentEmbeddingClient embedding = mock(AgentEmbeddingClient.class);
        when(embedding.enabled()).thenReturn(true);
        when(embedding.embed(anyString())).thenReturn(java.util.Optional.of(new double[]{1D, 0D, 0D}))
                .thenReturn(java.util.Optional.of(new double[]{1D, 0D}));
        AgentKnowledgeStore store = new AgentKnowledgeStore(null, null, embedding);

        store.upsert("p1", "product", "first", JsonNodeFactory.instance.objectNode());
        assertThrows(IllegalArgumentException.class,
                () -> store.upsert("p2", "product", "second", JsonNodeFactory.instance.objectNode()));
    }

    @Test
    void enabledEmbeddingProviderFailureDoesNotSilentlyUseLocalVector() {
        AgentEmbeddingClient embedding = mock(AgentEmbeddingClient.class);
        when(embedding.enabled()).thenReturn(true);
        when(embedding.embed(anyString())).thenReturn(java.util.Optional.empty());
        AgentKnowledgeStore store = new AgentKnowledgeStore(null, null, embedding);

        assertThrows(IllegalStateException.class,
                () -> store.upsert("p1", "product", "first", JsonNodeFactory.instance.objectNode()));
    }

    @Test
    void stalePersistedEmbeddingDimensionIsSkippedDuringRead() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        doReturn(hash).when(redis).opsForHash();
        AgentEmbeddingClient embedding = mock(AgentEmbeddingClient.class);
        when(embedding.enabled()).thenReturn(true);
        when(embedding.embed(anyString())).thenReturn(java.util.Optional.of(new double[]{1D, 0D, 0D, 0D,
                0D, 0D, 0D, 0D}));
        ObjectMapper mapper = new ObjectMapper();
        AgentKnowledgeStore.Document stale = new AgentKnowledgeStore.Document("stale", "product", "old",
                new double[]{1D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D,
                        0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D,
                        0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D,
                        0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D, 0D},
                JsonNodeFactory.instance.objectNode());
        when(hash.entries(anyString())).thenReturn(java.util.Map.of("stale", mapper.writeValueAsString(stale)));

        AgentKnowledgeStore store = new AgentKnowledgeStore(redis, mapper, embedding);
        store.upsert("current", "product", "current", JsonNodeFactory.instance.objectNode());

        assertDoesNotThrow(() -> store.search("current", 5, "product"));
        assertTrue(store.search("current", 5, "product").stream()
                .anyMatch(document -> document.id().equals("current")));
    }

    @Test
    void externalDeleteFailureRemainsReconciliationPending() {
        AgentVectorBackend backend = mock(AgentVectorBackend.class);
        when(backend.name()).thenReturn("test-vector");
        when(backend.remove("p1")).thenReturn(false).thenReturn(true);
        AgentKnowledgeStore store = new AgentKnowledgeStore(null, null, null, backend);
        store.upsert("p1", "product", "first", JsonNodeFactory.instance.objectNode());

        store.remove("p1");
        assertEquals(1, store.pendingExternalDeletes());
        assertEquals(1, store.reconcileExternalDeletes());
        assertEquals(0, store.pendingExternalDeletes());
        verify(backend).upsert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void externalWriteFailureIsDurablyTrackedAndReconciledWithoutLosingLocalKnowledge() {
        AgentVectorBackend backend = mock(AgentVectorBackend.class);
        when(backend.name()).thenReturn("test-vector");
        doThrow(new IllegalStateException("Elasticsearch unavailable")).doNothing().when(backend)
                .upsert(org.mockito.ArgumentMatchers.any());
        AgentKnowledgeStore store = new AgentKnowledgeStore(null, null, null, backend);

        store.upsert("p1", "product", "咖啡磨豆机", JsonNodeFactory.instance.objectNode());

        assertEquals(1, store.size());
        assertEquals(1, store.pendingExternalUpserts());
        assertEquals(1, store.reconcileExternalUpserts());
        assertEquals(0, store.pendingExternalUpserts());
        verify(backend, times(2)).upsert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void failedLongDocumentReplacementKeepsOneRetryMarkerAndRemovesOldChunksLocally() {
        AgentVectorBackend backend = mock(AgentVectorBackend.class);
        when(backend.name()).thenReturn("test-vector");
        doThrow(new IllegalStateException("Elasticsearch unavailable")).when(backend)
                .replaceDocument(anyString(), org.mockito.ArgumentMatchers.anyList());
        AgentKnowledgeStore store = new AgentKnowledgeStore(null, null, null, backend);

        store.replaceDocument("community:long", "community", "old ".repeat(900), JsonNodeFactory.instance.objectNode());
        store.replaceDocument("community:long", "community", "new short text", JsonNodeFactory.instance.objectNode());

        assertEquals(1, store.size());
        assertTrue(store.search("new short", 10, "community").stream()
                .anyMatch(document -> document.id().equals("community:long")));
        assertTrue(store.search("old", 10, "community").stream()
                .noneMatch(document -> document.id().startsWith("community:long#")));
        assertEquals(1, store.pendingExternalUpserts());
    }

    @Test
    void aNewUpsertSupersedesAPreviouslyFailedDelete() {
        AgentVectorBackend backend = mock(AgentVectorBackend.class);
        when(backend.name()).thenReturn("test-vector");
        when(backend.remove("p2")).thenReturn(false);
        doThrow(new IllegalStateException("Elasticsearch unavailable")).when(backend)
                .upsert(org.mockito.ArgumentMatchers.any());
        AgentKnowledgeStore store = new AgentKnowledgeStore(null, null, null, backend);

        store.upsert("p2", "product", "old", JsonNodeFactory.instance.objectNode());
        store.remove("p2");
        store.upsert("p2", "product", "new", JsonNodeFactory.instance.objectNode());

        assertEquals(0, store.pendingExternalDeletes());
        assertEquals(1, store.pendingExternalUpserts());
    }

    @Test
    void pendingWriteIsDiscardedWhenDeletionWinsTheProjectionRace() {
        AgentVectorBackend backend = mock(AgentVectorBackend.class);
        when(backend.name()).thenReturn("test-vector");
        doThrow(new IllegalStateException("Elasticsearch unavailable")).when(backend)
                .upsert(org.mockito.ArgumentMatchers.any());
        when(backend.remove("p1")).thenReturn(true);
        AgentKnowledgeStore store = new AgentKnowledgeStore(null, null, null, backend);

        store.upsert("p1", "product", "咖啡磨豆机", JsonNodeFactory.instance.objectNode());
        store.remove("p1");

        assertEquals(0, store.pendingExternalUpserts());
        assertEquals(0, store.reconcileExternalUpserts());
        verify(backend, times(1)).upsert(org.mockito.ArgumentMatchers.any());
    }
}

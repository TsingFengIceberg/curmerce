package cn.iocoder.yudao.curmerce.search;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SearchProjectionServiceTest {

    @Mock
    private ElasticsearchIndexClient indexClient;
    @Mock
    private SearchSourceClient sourceClient;

    private SearchProjectionService service;

    @BeforeEach
    void setUp() {
        SearchProperties properties = new SearchProperties(
                true, "http://127.0.0.1:19200", "products", "posts",
                "127.0.0.1:19092", "curmerce.events.v1", "curmerce-search-v1",
                "http://127.0.0.1:48080", "http://127.0.0.1:48083", java.time.Duration.ofSeconds(5), "");
        service = new SearchProjectionService(indexClient, sourceClient, properties, new SimpleMeterRegistry());
        lenient().when(indexClient.enabled()).thenReturn(true);
    }

    @Test
    void duplicateEventIsIdempotent() {
        when(indexClient.get("products", "11"))
                .thenReturn(null)
                .thenReturn(Map.of("sourceEventId", 7L));
        Map<String, Object> event = productEvent(11L, 7L, "new");

        service.project(event);
        service.project(event);

        verify(indexClient, times(1)).put(eq("products"), eq("11"), any());
    }

    @Test
    void olderEventCannotOverwriteNewerProjection() {
        when(indexClient.get("products", "11"))
                .thenReturn(null)
                .thenReturn(Map.of("sourceEventId", 8L));

        service.project(productEvent(11L, 8L, "new"));
        service.project(productEvent(11L, 7L, "old"));

        verify(indexClient, times(1)).put(eq("products"), eq("11"), any());
    }

    @Test
    void rebuildReplacesBothIndexesFromSourceSnapshots() {
        List<Map<String, Object>> products = List.of(Map.of("productId", 11L, "name", "P"));
        List<Map<String, Object>> posts = List.of(Map.of("postId", 21L, "title", "Post"));
        when(sourceClient.fetchProducts()).thenReturn(products);
        when(sourceClient.fetchPosts()).thenReturn(posts);

        SearchProjectionService.RebuildReport report = service.rebuildAll();

        assertThat(report.completed()).isTrue();
        assertThat(report.products()).isEqualTo(1);
        assertThat(report.posts()).isEqualTo(1);
        verify(indexClient).deleteAll("products");
        verify(indexClient).deleteAll("posts");
        verify(indexClient).bulkPut("products", products);
        verify(indexClient).bulkPut("posts", posts);
    }

    @Test
    void searchPageUsesShortLivedLocalCache() {
        ElasticsearchIndexClient.SearchPage page = new ElasticsearchIndexClient.SearchPage(List.of(Map.of("id", 1L)), 1L);
        when(indexClient.search("products", "phone", 1, 20)).thenReturn(page);

        assertThat(service.searchProducts("phone", 1, 20)).isSameAs(page);
        assertThat(service.searchProducts("phone", 1, 20)).isSameAs(page);

        verify(indexClient, times(1)).search("products", "phone", 1, 20);
    }

    @Test
    void reconciliationReportsSourceAndVisibleIndexMismatch() {
        when(sourceClient.fetchProducts()).thenReturn(List.of(Map.of("id", 1L), Map.of("id", 2L)));
        when(sourceClient.fetchPosts()).thenReturn(List.of(Map.of("id", 3L)));
        when(indexClient.countVisible("products")).thenReturn(1L);
        when(indexClient.countVisible("posts")).thenReturn(1L);

        SearchProjectionService.ProjectionReconciliationReport report = service.reconcile();

        assertThat(report.sourceProducts()).isEqualTo(2L);
        assertThat(report.indexedProducts()).isEqualTo(1L);
        assertThat(report.sourcePosts()).isEqualTo(1L);
        assertThat(report.matched()).isFalse();
    }

    private static Map<String, Object> productEvent(long productId, long eventId, String name) {
        return Map.of(
                "eventId", eventId,
                "eventType", "PRODUCT_CHANGED",
                "payload", Map.of(
                        "productId", productId,
                        "name", name,
                        "auditStatus", 2,
                        "saleStatus", 1,
                        "skus", List.of(Map.of("price", 100L))));
    }
}

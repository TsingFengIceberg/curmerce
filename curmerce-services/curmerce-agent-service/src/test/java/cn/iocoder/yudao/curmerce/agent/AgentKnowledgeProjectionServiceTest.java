package cn.iocoder.yudao.curmerce.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentKnowledgeProjectionServiceTest {
    private final AgentKnowledgeStore store = new AgentKnowledgeStore();
    private final AgentKnowledgeProjectionService service = new AgentKnowledgeProjectionService(store,
            new AgentKnowledgeProjectionCheckpoint(), new ObjectMapper(), new SimpleMeterRegistry());

    @Test
    void projectsVisibleProductAndIgnoresDuplicateAndOlderEvents() {
        service.project(productEvent(10L, 8L, "新款咖啡机", 2, 1));
        service.project(productEvent(10L, 8L, "重复旧标题", 2, 1));
        service.project(productEvent(10L, 7L, "乱序旧标题", 2, 1));

        assertThat(store.search("新款", 5, "product")).extracting(AgentKnowledgeStore.Document::id)
                .contains("product:10");
        String text = store.search("新款", 5, "product").getFirst().text();
        assertThat(text).contains("新款咖啡机").doesNotContain("重复旧标题", "乱序旧标题");
    }

    @Test
    void hidesProductAndPostByRemovingOnlyTheirOwnKnowledgeDocuments() {
        service.project(productEvent(10L, 8L, "可售商品", 2, 1));
        service.project(postEvent(12L, 6L, "可见帖子", 1));

        service.project(productEvent(10L, 9L, "已下架商品", 2, 0));
        service.project(postEvent(12L, 7L, "已隐藏帖子", 2));

        assertThat(store.search("可售", 10, "product")).isEmpty();
        assertThat(store.search("可见", 10, "community")).isEmpty();
    }

    @Test
    void longPostReplacementDoesNotLeaveOldChunksBehind() {
        service.project(postEvent(12L, 6L, "a".repeat(3_500), 1));
        service.project(postEvent(12L, 7L, "新版短帖子", 1));

        assertThat(store.search("新版", 10, "community"))
                .singleElement().extracting(AgentKnowledgeStore.Document::text).isEqualTo("社区帖子：新版短帖子\n正文：新版短帖子\n话题：[咖啡]\n关联商品：[10]");
    }

    private static Map<String, Object> productEvent(long productId, long eventId, String name, int auditStatus, int saleStatus) {
        return Map.of("eventId", eventId, "eventType", "PRODUCT_CHANGED", "payload", Map.of(
                "productId", productId, "name", name, "auditStatus", auditStatus, "saleStatus", saleStatus,
                "skus", List.of(Map.of("code", "sku-1", "price", 1999L, "stock", 3))));
    }

    private static Map<String, Object> postEvent(long postId, long eventId, String title, int status) {
        return Map.of("eventId", eventId, "eventType", "POST_CHANGED", "payload", Map.of(
                "postId", postId, "title", title, "content", title, "status", status,
                "productIds", List.of(10L), "topics", List.of("咖啡")));
    }
}

package cn.iocoder.yudao.curmerce.agent;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class AgentRetrievalService {

    private final RestClient coreClient;
    private final RestClient communityClient;
    private final CircuitBreaker coreCircuitBreaker;
    private final CircuitBreaker communityCircuitBreaker;

    public AgentRetrievalService(RestClient.Builder builder, AgentServiceProperties properties,
                                 CircuitBreakerRegistry circuitBreakerRegistry) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.coreClient = builder.clone().baseUrl(properties.coreBaseUrl()).requestFactory(factory).build();
        this.communityClient = builder.clone().baseUrl(properties.communityBaseUrl()).requestFactory(factory).build();
        this.coreCircuitBreaker = circuitBreakerRegistry.circuitBreaker("coreService");
        this.communityCircuitBreaker = circuitBreakerRegistry.circuitBreaker("communityService");
    }

    public AgentAssistRespDTO assist(String query) {
        List<String> degraded = new ArrayList<>();
        JsonNode products = fetch(coreClient, coreCircuitBreaker,
                "/app-api/commerce/catalog/product-page", query, "core", degraded);
        JsonNode posts = fetch(communityClient, communityCircuitBreaker,
                "/app-api/community/post/page", query, "community", degraded);
        int productCount = listSize(products);
        int postCount = listSize(posts);
        String summary = "检索到 " + productCount + " 个相关商品和 " + postCount + " 篇社区内容。"
                + (degraded.isEmpty() ? "" : "部分数据源暂时不可用，结果已降级。");
        return new AgentAssistRespDTO().setQuery(query).setSummary(summary)
                .setProducts(listNode(products)).setCommunityPosts(listNode(posts))
                .setDegradedSources(List.copyOf(degraded)).setModelBacked(false);
    }

    private JsonNode fetch(RestClient client, CircuitBreaker circuitBreaker, String path, String query,
                           String source, List<String> degraded) {
        try {
            CommonResult<JsonNode> response = circuitBreaker.executeSupplier(() -> client.get().uri(uri -> uri.path(path)
                                    .queryParam("keyword", query).queryParam("pageNo", 1).queryParam("pageSize", 6).build())
                            .retrieve().body(new ParameterizedTypeReference<>() {}));
            if (response == null || response.isError()) {
                degraded.add(source);
                return JsonNodeFactory.instance.objectNode();
            }
            return response.getData();
        } catch (RuntimeException ex) {
            degraded.add(source);
            log.warn("agent retrieval source unavailable: source={}, breakerState={}, reason={}",
                    source, circuitBreaker.getState(), ex.getMessage());
            return JsonNodeFactory.instance.objectNode();
        }
    }

    private static int listSize(JsonNode page) {
        return listNode(page).size();
    }

    private static JsonNode listNode(JsonNode page) {
        if (page != null && page.path("list").isArray()) {
            return page.path("list");
        }
        return JsonNodeFactory.instance.arrayNode();
    }
}

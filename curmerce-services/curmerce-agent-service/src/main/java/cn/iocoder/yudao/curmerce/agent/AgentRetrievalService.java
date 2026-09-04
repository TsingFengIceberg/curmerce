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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AgentRetrievalService {

    private final RestClient coreClient;
    private final RestClient communityClient;
    private final AgentServiceProperties properties;
    private final CircuitBreaker coreCircuitBreaker;
    private final CircuitBreaker communityCircuitBreaker;
    private final AgentKnowledgeStore knowledgeStore;
    private final AgentConversationMemory memory;
    private final AgentPolicyService policy;
    private final AgentInputPolicy inputPolicy;
    private final AgentAuditRecorder audit;
    private final AgentToolExecutor toolExecutor;
    private final AgentGroundingValidator groundingValidator;
    private final CircuitBreaker modelCircuitBreaker;
    @Autowired(required = false)
    private AgentChatModel modelClient;

    public AgentRetrievalService(RestClient.Builder builder, AgentServiceProperties properties,
                                 CircuitBreakerRegistry circuitBreakerRegistry) {
        this(builder, properties, circuitBreakerRegistry, new AgentKnowledgeStore(),
                new AgentConversationMemory(), new AgentPolicyService(io.micrometer.core.instrument.Metrics.globalRegistry, 30),
                new AgentInputPolicy(), new AgentAuditRecorder(io.micrometer.core.instrument.Metrics.globalRegistry),
                null, new AgentGroundingValidator());
    }

    public AgentRetrievalService(RestClient.Builder builder, AgentServiceProperties properties,
                                 CircuitBreakerRegistry circuitBreakerRegistry, AgentKnowledgeStore knowledgeStore,
                                 AgentConversationMemory memory, AgentPolicyService policy,
                                 AgentInputPolicy inputPolicy, AgentAuditRecorder audit) {
        this(builder, properties, circuitBreakerRegistry, knowledgeStore, memory, policy, inputPolicy, audit,
                null, new AgentGroundingValidator());
    }

    @Autowired
    public AgentRetrievalService(RestClient.Builder builder, AgentServiceProperties properties,
                                 CircuitBreakerRegistry circuitBreakerRegistry, AgentKnowledgeStore knowledgeStore,
                                 AgentConversationMemory memory, AgentPolicyService policy,
                                 AgentInputPolicy inputPolicy, AgentAuditRecorder audit,
                                 @Lazy AgentToolExecutor toolExecutor, AgentGroundingValidator groundingValidator) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        this.coreClient = builder.clone().baseUrl(properties.coreBaseUrl()).requestFactory(factory).build();
        this.communityClient = builder.clone().baseUrl(properties.communityBaseUrl()).requestFactory(factory).build();
        this.properties = properties;
        this.coreCircuitBreaker = circuitBreakerRegistry.circuitBreaker("coreService");
        this.communityCircuitBreaker = circuitBreakerRegistry.circuitBreaker("communityService");
        this.modelCircuitBreaker = circuitBreakerRegistry.circuitBreaker("modelService");
        this.knowledgeStore = knowledgeStore;
        this.memory = memory;
        this.policy = policy;
        this.inputPolicy = inputPolicy;
        this.audit = audit;
        this.toolExecutor = toolExecutor;
        this.groundingValidator = groundingValidator;
    }

    public AgentAssistRespDTO assist(String query) {
        return assist(query, null);
    }

    public AgentAssistRespDTO assist(String query, String authorization) {
        return assist(query, authorization, null);
    }

    public AgentAssistRespDTO assist(String query, String authorization, String conversationId) {
        String safeQuery = inputPolicy.sanitize(query);
        AgentPolicyService.Decision decision = policy.check(authorization);
        if (!decision.allowed()) {
            audit.record(authorization, "assist", "rate-limited");
            throw new AgentRateLimitException("Agent 请求频率超限");
        }
        audit.record(authorization, "assist", "accepted");
        String scopedConversationId = conversationId == null || conversationId.isBlank() ? null
                : AgentRequestContext.tenantScope() + ":" + AgentPrincipalHasher.hash(authorization) + ":" + conversationId.trim();
        List<String> degraded = new ArrayList<>();
        JsonNode products = fetch(coreClient, coreCircuitBreaker,
                "/app-api/commerce/catalog/product-page", safeQuery, "core", degraded);
        JsonNode posts = fetch(communityClient, communityCircuitBreaker,
                "/app-api/community/post/page", safeQuery, "community", degraded);
        int productCount = listSize(products);
        int postCount = listSize(posts);
        index("product", products);
        index("community", posts);
        String summary = "检索到 " + productCount + " 个相关商品和 " + postCount + " 篇社区内容。"
                + (degraded.isEmpty() ? "" : "部分数据源暂时不可用，结果已降级。");
        AgentAssistRespDTO response = new AgentAssistRespDTO().setQuery(safeQuery).setSummary(summary)
                .setProducts(listNode(products)).setCommunityPosts(listNode(posts))
                .setDegradedSources(degraded).setModelBacked(false)
                .setReferences(references(products, posts, safeQuery));
        if (modelClient != null && modelClient.enabled()) {
            try {
                String memoryContext = scopedConversationId == null ? "" : memory.history(scopedConversationId).stream()
                        .map(message -> message.role() + ": " + message.content()).reduce("", (a, b) -> a + "\n" + b);
                String modelContext = context(products, posts, safeQuery) + memoryContext;
                SpringAiCompatibleChatClient.ModelAnswer answer = completeModel(safeQuery, modelContext);
                List<SpringAiCompatibleChatClient.ToolResult> toolResults = new ArrayList<>();
                List<SpringAiCompatibleChatClient.ToolCall> executedToolCalls = new ArrayList<>();
                if (toolExecutor != null && !answer.toolCalls().isEmpty()) {
                    if (properties.maxToolRounds() <= 0) degraded.add("model-tool-loop");
                    java.util.Set<String> executedCallIds = new java.util.HashSet<>();
                    for (int round = 0; round < properties.maxToolRounds() && !answer.toolCalls().isEmpty(); round++) {
                        List<SpringAiCompatibleChatClient.ToolCall> roundCalls = answer.toolCalls().stream().limit(8)
                                .filter(call -> executedCallIds.add(call.id() == null || call.id().isBlank()
                                        ? call.name() + ":" + call.arguments() : call.id())).toList();
                        List<SpringAiCompatibleChatClient.ToolResult> roundResults = roundCalls.stream()
                                .map(call -> toolExecutor.executeForModel(authorization, call))
                                .map(AgentRetrievalService::boundToolResult).toList();
                        if (roundResults.isEmpty()) {
                            degraded.add("model-tool-loop");
                            break;
                        }
                        executedToolCalls.addAll(roundCalls);
                        toolResults.addAll(roundResults);
                        answer = completeModelWithToolResults(safeQuery, modelContext, answer, roundResults);
                        if (round == properties.maxToolRounds() - 1 && !answer.toolCalls().isEmpty()) {
                            degraded.add("model-tool-loop");
                        }
                    }
                }
                // Model output is untrusted external input. Redact credentials
                // before it reaches the API response, conversation memory, or
                // grounding/evaluation consumers.
                String finalAnswer = AgentInputPolicy.redactSecrets(answer.answer());
                if (finalAnswer == null || finalAnswer.isBlank()) {
                    finalAnswer = toolResults.stream().map(SpringAiCompatibleChatClient.ToolResult::content)
                            .filter(value -> value != null && !value.isBlank())
                            .map(AgentInputPolicy::redactSecrets)
                            .reduce("工具已执行，但模型未返回文字说明：", (a, b) -> a + "\n" + b);
                }
                response.setModelBacked(true).setModelAnswer(finalAnswer).setUsage(answer.usage()).setToolCalls(executedToolCalls)
                        .setToolResults(toolResults)
                        .setGroundingWarnings(groundingValidator.validate(finalAnswer, modelContext));
            } catch (RuntimeException ex) {
                degraded.add("model");
                response.setDegradedSources(List.copyOf(degraded));
            }
        }
        if (scopedConversationId != null) {
            memory.append(scopedConversationId, "user", safeQuery);
            memory.append(scopedConversationId, "assistant", response.getModelAnswer() == null ? summary : response.getModelAnswer());
        }
        return response.setDegradedSources(List.copyOf(degraded));
    }

    private static SpringAiCompatibleChatClient.ToolResult boundToolResult(
            SpringAiCompatibleChatClient.ToolResult result) {
        if (result == null) return new SpringAiCompatibleChatClient.ToolResult("unknown", "unknown", false, "工具未返回结果");
        String content = result.content() == null ? "" : result.content();
        if (content.length() <= 8000) return result;
        return new SpringAiCompatibleChatClient.ToolResult(result.callId(), result.name(), result.success(),
                content.substring(0, 8000) + "\n[工具结果已截断]");
    }

    private SpringAiCompatibleChatClient.ModelAnswer completeModel(String query, String context) {
        return modelCircuitBreaker.executeSupplier(() -> modelClient.complete(query, context));
    }

    private SpringAiCompatibleChatClient.ModelAnswer completeModelWithToolResults(String query, String context,
                                                                                   SpringAiCompatibleChatClient.ModelAnswer previous,
                                                                                   List<SpringAiCompatibleChatClient.ToolResult> results) {
        return modelCircuitBreaker.executeSupplier(() -> modelClient.completeWithToolResults(query, context, previous, results));
    }

    /** Runs the provider probe through the production model circuit breaker. */
    public SpringAiCompatibleChatClient.ModelAnswer modelSmoke() {
        if (modelClient == null || !modelClient.enabled()) return null;
        return completeModel("只回复 OK，不调用任何工具。", "Provider readiness probe");
    }

    /** Exposes aggregate circuit state without returning provider content. */
    public Map<String, Object> modelCircuitState() {
        CircuitBreaker.Metrics metrics = modelCircuitBreaker.getMetrics();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", modelCircuitBreaker.getName());
        value.put("state", modelCircuitBreaker.getState().name());
        value.put("failureRate", metrics.getFailureRate());
        value.put("slowCallRate", metrics.getSlowCallRate());
        value.put("bufferedCalls", metrics.getNumberOfBufferedCalls());
        value.put("failedCalls", metrics.getNumberOfFailedCalls());
        value.put("slowCalls", metrics.getNumberOfSlowCalls());
        value.put("notPermittedCalls", metrics.getNumberOfNotPermittedCalls());
        return Map.copyOf(value);
    }

    public JsonNode searchProducts(String query, String authorization) {
        return searchSource(query, authorization, coreClient, coreCircuitBreaker, "core");
    }

    public JsonNode searchCommunity(String query, String authorization) {
        return searchSource(query, authorization, communityClient, communityCircuitBreaker, "community");
    }

    private JsonNode searchSource(String query, String authorization, RestClient client,
                                  CircuitBreaker breaker, String source) {
        String safeQuery = inputPolicy.sanitize(query);
        if (!policy.check(authorization).allowed()) {
            audit.record(authorization, "tool-search", "rate-limited");
            throw new AgentRateLimitException("Agent 请求频率超限");
        }
        List<String> degraded = new ArrayList<>();
        JsonNode result = fetch(client, breaker, source.equals("core")
                ? "/app-api/commerce/catalog/product-page" : "/app-api/community/post/page", safeQuery, source, degraded);
        index(source, result);
        audit.record(authorization, "tool-search", degraded.isEmpty() ? "accepted" : "degraded");
        return result;
    }

    private void index(String source, JsonNode page) {
        int index = 0;
        for (JsonNode item : listNode(page)) {
            knowledgeStore.upsert(source + ":" + item.path("id").asText(String.valueOf(index++)), source, item.toString(), item);
        }
    }

    private String context(JsonNode products, JsonNode posts, String query) {
        StringBuilder context = new StringBuilder();
        listNode(products).forEach(item -> context.append("商品[商品:").append(item.path("id").asText("unknown"))
                .append("]: ").append(item.toString()).append('\n'));
        listNode(posts).forEach(item -> context.append("社区[帖子:").append(item.path("id").asText("unknown"))
                .append("]: ").append(item.toString()).append('\n'));
        for (AgentKnowledgeStore.Document document : knowledgeStore.search(query, 8)) {
            context.append("知识库[").append(document.source()).append(":").append(document.id())
                    .append("]: ").append(document.text()).append('\n');
        }
        int max = properties.maxContextChars();
        return context.length() > max ? context.substring(0, max) : context.toString();
    }

    /** Exposes display-safe, bounded source data for UI and audit consumers. */
    private List<AgentAssistRespDTO.AgentSourceReference> references(JsonNode products, JsonNode posts, String query) {
        List<AgentAssistRespDTO.AgentSourceReference> result = new ArrayList<>();
        listNode(products).forEach(item -> {
            String id = item.path("id").asText("").trim();
            if (id.isBlank()) return;
            result.add(new AgentAssistRespDTO.AgentSourceReference("product", id,
                    display(item, "name", "商品"), excerpt(item, "description", "暂无商品描述"), "/products/" + id));
        });
        listNode(posts).forEach(item -> {
            String id = item.path("id").asText("").trim();
            if (id.isBlank()) return;
            result.add(new AgentAssistRespDTO.AgentSourceReference("community", id,
                    display(item, "title", "社区帖子"), excerpt(item, "content", "暂无帖子摘要"), "/community/" + id));
        });
        for (AgentKnowledgeStore.Document document : knowledgeStore.search(query, 8)) {
            String title = document.metadata() == null ? "知识文档"
                    : displayMetadata(document.metadata(), "title", "知识文档");
            result.add(new AgentAssistRespDTO.AgentSourceReference(document.source(), document.id(), title,
                    boundExcerpt(document.text()), null));
        }
        return result.stream().distinct().limit(12).toList();
    }

    private static String display(JsonNode item, String field, String fallback) {
        String value = item.path(field).asText("").trim();
        return value.isBlank() ? fallback : boundExcerpt(value);
    }

    private static String displayMetadata(JsonNode metadata, String field, String fallback) {
        String value = metadata.path(field).asText("").trim();
        return value.isBlank() ? fallback : boundExcerpt(value);
    }

    private static String excerpt(JsonNode item, String field, String fallback) {
        return boundExcerpt(item.path(field).asText(fallback));
    }

    private static String boundExcerpt(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180) + "...";
    }

    private JsonNode fetch(RestClient client, CircuitBreaker circuitBreaker, String path, String query,
                           String source, List<String> degraded) {
        try {
            CommonResult<JsonNode> response = circuitBreaker.executeSupplier(() -> client.get().uri(uri -> uri.path(path)
                                    .queryParam("keyword", query).queryParam("pageNo", 1).queryParam("pageSize", 6).build())
                            .header("tenant-id", AgentRequestContext.tenantId())
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

    public static class AgentRateLimitException extends RuntimeException {
        public AgentRateLimitException(String message) { super(message); }
    }
}

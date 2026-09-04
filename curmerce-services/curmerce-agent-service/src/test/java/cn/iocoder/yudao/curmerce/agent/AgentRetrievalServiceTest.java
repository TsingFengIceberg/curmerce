package cn.iocoder.yudao.curmerce.agent;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.time.Duration;
import java.util.List;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRetrievalServiceTest {

    @Test
    void assistDegradesWhenBothReadOnlySourcesAreUnavailable() {
        AgentServiceProperties properties = new AgentServiceProperties("http://127.0.0.1:1",
                "http://127.0.0.1:2", Duration.ofMillis(100), Duration.ofMillis(100));
        AgentRetrievalService service = new AgentRetrievalService(RestClient.builder(), properties,
                CircuitBreakerRegistry.ofDefaults());

        AgentAssistRespDTO response = service.assist("manual coffee grinder");

        assertEquals(List.of("core", "community"), response.getDegradedSources());
        assertEquals(0, response.getProducts().size());
        assertEquals(0, response.getCommunityPosts().size());
        assertFalse(response.isModelBacked());
    }

    @Test
    void modelToolCallIsExecutedAndFedBackBeforeFinalAnswer() throws Exception {
        AgentServiceProperties properties = new AgentServiceProperties("http://127.0.0.1:1",
                "http://127.0.0.1:2", Duration.ofMillis(50), Duration.ofMillis(50));
        AgentToolExecutor executor = mock(AgentToolExecutor.class);
        when(executor.executeForModel(anyString(), any())).thenReturn(
                new SpringAiCompatibleChatClient.ToolResult("call-1", "product-search", true,
                        "[{\"id\":1,\"name\":\"咖啡磨豆机\"}]"));
        AgentChatModel model = mock(AgentChatModel.class);
        when(model.enabled()).thenReturn(true);
        SpringAiCompatibleChatClient.ModelAnswer first = new SpringAiCompatibleChatClient.ModelAnswer("",
                null, List.of(new SpringAiCompatibleChatClient.ToolCall("call-1", "product-search",
                        JsonNodeFactory.instance.objectNode().put("query", "咖啡"))));
        SpringAiCompatibleChatClient.ModelAnswer finalAnswer = new SpringAiCompatibleChatClient.ModelAnswer(
                "找到咖啡磨豆机。", null, List.of());
        when(model.complete(anyString(), anyString())).thenReturn(first);
        when(model.completeWithToolResults(anyString(), anyString(), any(), any())).thenReturn(finalAnswer);

        AgentRetrievalService service = serviceWith(properties, executor);
        SimpleField.set(AgentRetrievalService.class, service, "modelClient", model);

        AgentAssistRespDTO response = service.assist("找咖啡");

        assertTrue(response.isModelBacked());
        assertEquals("找到咖啡磨豆机。", response.getModelAnswer());
        assertEquals(1, response.getToolCalls().size());
        assertEquals("call-1", response.getToolCalls().getFirst().id());
        assertEquals(1, response.getToolResults().size());
    }

    @Test
    void buildsBoundedNavigableReferencesForProductAndCommunityResponseSnapshots() throws Exception {
        AgentRetrievalService service = serviceWith(new AgentServiceProperties("http://127.0.0.1:1", "http://127.0.0.1:2",
                Duration.ofMillis(100), Duration.ofMillis(100)), null);
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode products = objectMapper.readTree("{\"list\":[{\"id\":101,\"name\":\"Coffee grinder\",\"description\":\"Steel burrs\"}]}");
        com.fasterxml.jackson.databind.JsonNode posts = objectMapper.readTree("{\"list\":[{\"id\":202,\"title\":\"Coffee review\",\"content\":\"Useful grinder notes\"}]}");
        java.lang.reflect.Method references = AgentRetrievalService.class.getDeclaredMethod("references",
                com.fasterxml.jackson.databind.JsonNode.class, com.fasterxml.jackson.databind.JsonNode.class, String.class);
        references.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<AgentAssistRespDTO.AgentSourceReference> result =
                (List<AgentAssistRespDTO.AgentSourceReference>) references.invoke(service, products, posts, "coffee");

        assertTrue(result.stream().anyMatch(reference -> reference.source().equals("product")
                && reference.id().equals("101") && reference.path().equals("/products/101")
                && reference.title().equals("Coffee grinder")));
        assertTrue(result.stream().anyMatch(reference -> reference.source().equals("community")
                && reference.id().equals("202") && reference.path().equals("/community/202")
                && reference.title().equals("Coffee review")));
        assertTrue(result.size() <= 12);
    }

    private static AgentRetrievalService serviceWith(AgentServiceProperties properties, AgentToolExecutor executor) {
        return serviceWith(RestClient.builder(), properties, executor);
    }

    private static AgentRetrievalService serviceWith(RestClient.Builder builder, AgentServiceProperties properties,
                                                     AgentToolExecutor executor) {
        io.micrometer.core.instrument.simple.SimpleMeterRegistry metrics = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        return new AgentRetrievalService(builder, properties, CircuitBreakerRegistry.ofDefaults(),
                new AgentKnowledgeStore(), new AgentConversationMemory(), new AgentPolicyService(metrics, 30),
                new AgentInputPolicy(), new AgentAuditRecorder(metrics), executor, new AgentGroundingValidator());
    }

    private static final class SimpleField {
        static void set(Class<?> type, Object target, String name, Object value) throws Exception {
            java.lang.reflect.Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        }
    }
}

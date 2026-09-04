package cn.iocoder.yudao.curmerce.agent;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class AgentToolExecutorTest {
    @Test
    void modelCannotInvokeSensitiveToolWithoutExplicitConfirmation() {
        AgentCoreClient core = mock(AgentCoreClient.class);
        AgentRetrievalService retrieval = mock(AgentRetrievalService.class);
        AgentConfirmationService confirmations = mock(AgentConfirmationService.class);
        AgentToolExecutor executor = new AgentToolExecutor(new AgentToolRegistry(), core, retrieval, confirmations,
                new AgentInputPolicy(), new AgentAuditRecorder(new SimpleMeterRegistry()));

        var result = executor.executeForModel("Bearer user", new SpringAiCompatibleChatClient.ToolCall(
                "call-1", "refund-request", JsonNodeFactory.instance.objectNode()));

        assertFalse(result.success());
        assertTrue(result.content().contains("需要用户"));
        verifyNoInteractions(core, confirmations);
    }

    @Test
    void blankConfirmationIsRejectedLikeMissingConfirmation() {
        AgentCoreClient core = mock(AgentCoreClient.class);
        AgentRetrievalService retrieval = mock(AgentRetrievalService.class);
        AgentConfirmationService confirmations = mock(AgentConfirmationService.class);
        AgentToolExecutor executor = new AgentToolExecutor(new AgentToolRegistry(), core, retrieval, confirmations,
                new AgentInputPolicy(), new AgentAuditRecorder(new SimpleMeterRegistry()));

        var error = org.junit.jupiter.api.Assertions.assertThrows(AgentToolExecutor.ToolConfirmationRequiredException.class,
                () -> executor.execute("Bearer user", "refund-request", JsonNodeFactory.instance.objectNode(), ""));
        assertTrue(error.getMessage().contains("需要用户"));
        verifyNoInteractions(core, confirmations);
    }

    @Test
    void unknownModelToolIsRejectedWithoutExecutingAnything() {
        AgentCoreClient core = mock(AgentCoreClient.class);
        AgentRetrievalService retrieval = mock(AgentRetrievalService.class);
        AgentConfirmationService confirmations = mock(AgentConfirmationService.class);
        AgentToolExecutor executor = new AgentToolExecutor(new AgentToolRegistry(), core, retrieval, confirmations,
                new AgentInputPolicy(), new AgentAuditRecorder(new SimpleMeterRegistry()));

        var result = executor.executeForModel("Bearer user", new SpringAiCompatibleChatClient.ToolCall(
                "call-2", "delete-all-orders", JsonNodeFactory.instance.objectNode()));

        assertFalse(result.success());
        verifyNoInteractions(core, retrieval, confirmations);
    }

    @Test
    void toolArgumentsMustBeAnObject() {
        AgentCoreClient core = mock(AgentCoreClient.class);
        AgentRetrievalService retrieval = mock(AgentRetrievalService.class);
        AgentConfirmationService confirmations = mock(AgentConfirmationService.class);
        AgentToolExecutor executor = new AgentToolExecutor(new AgentToolRegistry(), core, retrieval, confirmations,
                new AgentInputPolicy(), new AgentAuditRecorder(new SimpleMeterRegistry()));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> executor.execute("Bearer user", "platform-rules", JsonNodeFactory.instance.arrayNode(), null));
    }

    @Test
    void oversizedToolArgumentsAreRejected() {
        AgentCoreClient core = mock(AgentCoreClient.class);
        AgentRetrievalService retrieval = mock(AgentRetrievalService.class);
        AgentConfirmationService confirmations = mock(AgentConfirmationService.class);
        AgentToolExecutor executor = new AgentToolExecutor(new AgentToolRegistry(), core, retrieval, confirmations,
                new AgentInputPolicy(), new AgentAuditRecorder(new SimpleMeterRegistry()));
        var args = JsonNodeFactory.instance.objectNode().put("query", "x".repeat(9000));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> executor.execute("Bearer user", "product-search", args, null));
    }

    @Test
    void toolArgumentsRejectUnknownFieldsAndControlCharacters() {
        AgentCoreClient core = mock(AgentCoreClient.class);
        AgentRetrievalService retrieval = mock(AgentRetrievalService.class);
        AgentConfirmationService confirmations = mock(AgentConfirmationService.class);
        AgentToolExecutor executor = new AgentToolExecutor(new AgentToolRegistry(), core, retrieval, confirmations,
                new AgentInputPolicy(), new AgentAuditRecorder(new SimpleMeterRegistry()));
        var unknown = JsonNodeFactory.instance.objectNode().put("query", "咖啡").put("authorization", "Bearer secret");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> executor.execute("Bearer user", "product-search", unknown, null));
        var control = JsonNodeFactory.instance.objectNode().put("query", "咖啡\u0000");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> executor.execute("Bearer user", "product-search", control, null));
    }

    @Test
    void modelToolExecutionTimesOutAndReturnsSafeFailure() throws Exception {
        AgentCoreClient core = mock(AgentCoreClient.class);
        AgentRetrievalService retrieval = mock(AgentRetrievalService.class);
        when(retrieval.searchProducts(anyString(), anyString())).thenAnswer(invocation -> {
            Thread.sleep(500);
            return JsonNodeFactory.instance.objectNode();
        });
        AgentConfirmationService confirmations = mock(AgentConfirmationService.class);
        AgentToolExecutor executor = new AgentToolExecutor(new AgentToolRegistry(), core, retrieval, confirmations,
                new AgentInputPolicy(), new AgentAuditRecorder(new SimpleMeterRegistry()), 100L);

        var result = executor.executeForModel("Bearer user", new SpringAiCompatibleChatClient.ToolCall(
                "call-timeout", "product-search", JsonNodeFactory.instance.objectNode().put("query", "咖啡")));
        assertFalse(result.success());
        assertTrue(result.content().contains("超时"));
    }
}

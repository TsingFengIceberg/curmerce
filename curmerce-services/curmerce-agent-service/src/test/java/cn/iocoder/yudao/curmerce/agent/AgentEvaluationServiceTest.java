package cn.iocoder.yudao.curmerce.agent;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEvaluationServiceTest {
    @Test
    void corpusContainsAuthorizationAndInjectionCases() {
        AgentEvaluationService service = new AgentEvaluationService(new AgentInputPolicy());
        assertEquals(16, service.cases().size());
        assertTrue(service.cases().stream().anyMatch(value -> value.name().equals("ownership") && !value.expectedAllowed()));
        assertTrue(service.policyPasses());
        assertTrue(service.cases().stream().anyMatch(value -> value.name().equals("confirmation-bypass") && !value.expectedAllowed()));
        assertTrue(service.runSuite().passed());
    }

    @Test
    void evaluatorRejectsUngroundedOrSecretLeakingAnswers() {
        AgentEvaluationService service = new AgentEvaluationService(new AgentInputPolicy());
        assertTrue(service.evaluate("商品价格是 ¥12.50", "商品价格 ¥12.50").passed());
        assertEquals(false, service.evaluate("订单号 1 的 password=secret", "").passed());
        assertEquals(false, service.evaluate("库存为 9 件", "库存为 3 件").passed());
    }

    @Test
    void inputPolicyRedactsCredentialsWithoutForwardingTheirValues() {
        String safe = new AgentInputPolicy().sanitize("api_key=secret-value 帮我找咖啡机");
        assertTrue(safe.contains("api_key=[REDACTED]"));
        assertEquals(false, safe.contains("secret-value"));
    }
}

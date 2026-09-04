package cn.iocoder.yudao.curmerce.agent;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPolicyServiceTest {
    @Test
    void requestQuotaIsEnforcedPerPrincipal() {
        AgentPolicyService policy = new AgentPolicyService(new SimpleMeterRegistry(), 2);
        assertTrue(policy.check("u1").allowed());
        assertTrue(policy.check("u1").allowed());
        assertFalse(policy.check("u1").allowed());
        assertTrue(policy.check("u2").allowed());
    }
}

package cn.iocoder.yudao.curmerce.agent;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentUsageRecorderTest {
    @Test
    void keepsProcessSummaryWhenRedisIsNotConfigured() {
        AgentUsageRecorder recorder = new AgentUsageRecorder(new SimpleMeterRegistry());
        recorder.record("local", 10, 4, Duration.ofMillis(12), 0.25D);
        recorder.record("local", 3, 2, Duration.ofMillis(8), 0.10D);

        var summary = recorder.summary();
        assertEquals(2L, summary.requests());
        assertEquals(13L, summary.promptTokens());
        assertEquals(6L, summary.completionTokens());
        assertEquals(0.35D, summary.cost(), 0.000001D);
    }

    @Test
    void localQuotaAccountsForInFlightRequestsBeforeTheyAreRecorded() {
        AgentUsageRecorder recorder = new AgentUsageRecorder(new SimpleMeterRegistry(), 10L, 0D);
        assertEquals(true, recorder.allow("user-a", "model-a", 8, 0D));
        assertEquals(false, recorder.allow("user-a", "model-a", 3, 0D));

        recorder.record("user-a", "model-a", 8, 0, Duration.ofMillis(1), 0D);
        assertEquals(false, recorder.allow("user-a", "model-a", 3, 0D));
        assertEquals(true, recorder.allow("user-b", "model-a", 10, 0D));
    }
}

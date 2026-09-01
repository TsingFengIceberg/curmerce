package cn.iocoder.yudao.curmerce.agent;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/** Records model usage without persisting prompts or secrets. */
@Component
public class AgentUsageRecorder {
    private final MeterRegistry meterRegistry;
    private final AtomicReference<Usage> last = new AtomicReference<>(new Usage(0, 0, 0, 0, "none"));

    public AgentUsageRecorder(MeterRegistry meterRegistry) { this.meterRegistry = meterRegistry; }

    public Usage record(String provider, int promptTokens, int completionTokens, Duration latency, double cost) {
        Usage usage = new Usage(Math.max(0, promptTokens), Math.max(0, completionTokens),
                Math.max(0, latency.toMillis()), Math.max(0D, cost), provider == null ? "unknown" : provider);
        last.set(usage);
        meterRegistry.counter("curmerce.agent.requests", "provider", usage.provider()).increment();
        meterRegistry.counter("curmerce.agent.tokens", "kind", "prompt").increment(usage.promptTokens());
        meterRegistry.counter("curmerce.agent.tokens", "kind", "completion").increment(usage.completionTokens());
        meterRegistry.counter("curmerce.agent.cost", "provider", usage.provider()).increment(usage.cost());
        meterRegistry.timer("curmerce.agent.latency", "provider", usage.provider()).record(latency);
        return usage;
    }

    public Usage latest() { return last.get(); }

    public record Usage(int promptTokens, int completionTokens, long latencyMillis, double cost, String provider) { }
}

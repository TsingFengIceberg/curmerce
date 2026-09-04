package cn.iocoder.yudao.curmerce.agent;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** User-scoped rate limits, quotas and an in-memory audit trail with no prompt persistence. */
@Component
public class AgentPolicyService {
    private final MeterRegistry metrics;
    private final int requestsPerMinute;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;

    public AgentPolicyService(MeterRegistry metrics, int requestsPerMinute) {
        this(metrics, requestsPerMinute, (StringRedisTemplate) null);
    }

    @Autowired
    public AgentPolicyService(MeterRegistry metrics,
                              @Value("${curmerce.agent.requests-per-minute:30}") int requestsPerMinute,
                              org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> provider) {
        this(metrics, requestsPerMinute, provider.getIfAvailable());
    }

    private AgentPolicyService(MeterRegistry metrics, int requestsPerMinute, StringRedisTemplate redis) {
        this.metrics = metrics; this.requestsPerMinute = Math.max(1, requestsPerMinute); this.redis = redis;
    }

    public Decision check(String principal) {
        String key = principal == null || principal.isBlank() ? "anonymous" : principal;
        String scope = AgentRequestContext.tenantScope() + ":" + AgentPrincipalHasher.hash(key);
        long minute = Instant.now().getEpochSecond() / 60;
        if (redis != null) {
            try {
                String redisKey = "curmerce:agent:quota:v2:" + scope + ":" + minute;
                Long count = redis.opsForValue().increment(redisKey);
                if (count != null && count == 1L) redis.expire(redisKey, java.time.Duration.ofSeconds(75));
                boolean allowed = count != null && count <= requestsPerMinute;
                metrics.counter("curmerce.agent.policy.requests", "allowed", String.valueOf(allowed)).increment();
                if (!allowed) metrics.counter("curmerce.agent.policy.denied", "reason", "rate-limit").increment();
                return new Decision(allowed, requestsPerMinute - (count == null ? requestsPerMinute : count.intValue()));
            } catch (RuntimeException ex) {
                // The Redis counter is the cross-instance rate-limit authority.
                // Falling back to a local counter during an outage would let a
                // caller multiply the limit by the number of Agent instances.
                metrics.counter("curmerce.agent.policy.denied", "reason", "rate-limit-store-unavailable").increment();
                return new Decision(false, 0);
            }
        }
        Window window = windows.compute(scope, (ignored, existing) -> existing == null || existing.minute() != minute
                ? new Window(minute, 1) : new Window(minute, existing.count() + 1));
        boolean allowed = window.count() <= requestsPerMinute;
        metrics.counter("curmerce.agent.policy.requests", "allowed", String.valueOf(allowed)).increment();
        if (!allowed) metrics.counter("curmerce.agent.policy.denied", "reason", "rate-limit").increment();
        return new Decision(allowed, requestsPerMinute - window.count());
    }

    public record Decision(boolean allowed, int remaining) { }
    private record Window(long minute, int count) { }
}

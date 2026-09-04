package cn.iocoder.yudao.curmerce.agent;

import io.micrometer.core.instrument.MeterRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.DoubleAdder;

/** Records model usage without persisting prompts or secrets. */
@Component
public class AgentUsageRecorder {
    private static final String REDIS_KEY = "curmerce:agent:usage:v1:latest";
    private static final String INFLIGHT_TOKEN_KEY = "curmerce:agent:usage:v1:inflight-tokens";
    private static final String INFLIGHT_COST_KEY = "curmerce:agent:usage:v1:inflight-cost-micros";
    private static final String SCOPES_KEY = "curmerce:agent:usage:v1:scopes";
    private static final DefaultRedisScript<Long> RESERVE_BUDGET = new DefaultRedisScript<>("""
            local tokens = tonumber(redis.call('HGET', KEYS[1], 'promptTokens') or '0')
                + tonumber(redis.call('HGET', KEYS[1], 'completionTokens') or '0')
                + tonumber(redis.call('GET', KEYS[2]) or '0')
            local cost = tonumber(redis.call('HGET', KEYS[1], 'costMicros') or '0')
                + tonumber(redis.call('GET', KEYS[3]) or '0')
            if tonumber(ARGV[2]) > 0 and tokens + tonumber(ARGV[1]) > tonumber(ARGV[2]) then return 0 end
            if tonumber(ARGV[4]) > 0 and cost + tonumber(ARGV[3]) > tonumber(ARGV[4]) then return 0 end
            redis.call('INCRBY', KEYS[2], ARGV[1])
            redis.call('INCRBY', KEYS[3], ARGV[3])
            redis.call('EXPIRE', KEYS[2], 900)
            redis.call('EXPIRE', KEYS[3], 900)
            return 1
            """, Long.class);
    private final MeterRegistry meterRegistry;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AtomicReference<Usage> last = new AtomicReference<>(new Usage(0, 0, 0, 0, "none"));
    private final LongAdder requests = new LongAdder();
    private final LongAdder promptTokens = new LongAdder();
    private final LongAdder completionTokens = new LongAdder();
    private final java.util.concurrent.atomic.DoubleAdder cost = new java.util.concurrent.atomic.DoubleAdder();
    private final long dailyTokenLimit;
    private final double dailyCostLimit;
    private final ThreadLocal<Reservation> reservation = new ThreadLocal<>();
    private final ThreadLocal<String> principal = new ThreadLocal<>();
    private final Map<String, Summary> localScopes = new ConcurrentHashMap<>();
    private final Map<String, Object> quotaLocks = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> localInflightTokens = new ConcurrentHashMap<>();
    private final Map<String, DoubleAdder> localInflightCost = new ConcurrentHashMap<>();
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private AgentUsageJdbcArchive jdbcArchive;

    public AgentUsageRecorder(MeterRegistry meterRegistry) { this(meterRegistry, null, null, 0L, 0D); }

    AgentUsageRecorder(MeterRegistry meterRegistry, long dailyTokenLimit, double dailyCostLimit) {
        this(meterRegistry, null, null, dailyTokenLimit, dailyCostLimit);
    }

    @Autowired
    public AgentUsageRecorder(MeterRegistry meterRegistry, ObjectProvider<StringRedisTemplate> provider, ObjectMapper objectMapper,
                              AgentServiceProperties properties) {
        this(meterRegistry, provider.getIfAvailable(), objectMapper,
                properties == null ? 0L : properties.dailyTokenLimit(), properties == null ? 0D : properties.dailyCostLimit());
    }

    private AgentUsageRecorder(MeterRegistry meterRegistry, StringRedisTemplate redis, ObjectMapper objectMapper,
                               long dailyTokenLimit, double dailyCostLimit) {
        this.meterRegistry = meterRegistry; this.redis = redis; this.objectMapper = objectMapper;
        this.dailyTokenLimit = Math.max(0L, dailyTokenLimit); this.dailyCostLimit = Math.max(0D, dailyCostLimit);
    }

    /** Reject a model call before network I/O when a configured daily budget is exhausted. */
    public boolean allow(int estimatedPromptTokens) {
        return allow("anonymous", "unknown", estimatedPromptTokens, 0D);
    }

    /** Atomically reserve an estimated model call budget across Agent instances. */
    public boolean allow(int estimatedPromptTokens, double estimatedCost) {
        return allow("anonymous", "unknown", estimatedPromptTokens, estimatedCost);
    }

    /** Atomically reserves a budget bucket for one principal and model. */
    public boolean allow(String principal, String model, int estimatedPromptTokens, double estimatedCost) {
        String scope = scope(principal, model);
        synchronized (quotaLocks.computeIfAbsent(scope, ignored -> new Object())) {
            Summary scoped = scopedSummary(scope);
            long prompt = Math.max(0, estimatedPromptTokens);
            double costEstimate = Math.max(0D, estimatedCost);
            long projectedTokens = scoped.promptTokens() + scoped.completionTokens() + prompt
                    + localInflightTokens.getOrDefault(scope, new LongAdder()).sum();
            double projectedCost = scoped.cost() + costEstimate
                    + localInflightCost.getOrDefault(scope, new DoubleAdder()).sum();
            boolean allowed = (dailyTokenLimit <= 0 || projectedTokens <= dailyTokenLimit)
                    && (dailyCostLimit <= 0D || projectedCost <= dailyCostLimit);
            boolean localReservation = false;
            String inflightTokenKey = null;
            String inflightCostKey = null;
            long costMicros = Math.round(costEstimate * 1_000_000D);
            if (allowed && redis != null && objectMapper != null && (dailyTokenLimit > 0 || dailyCostLimit > 0D)) {
                try {
                    String totalsKey = key(REDIS_KEY) + ":scope:" + scope;
                    inflightTokenKey = key(INFLIGHT_TOKEN_KEY) + ":" + scope;
                    inflightCostKey = key(INFLIGHT_COST_KEY) + ":" + scope;
                    Long result = redis.execute(RESERVE_BUDGET,
                            java.util.List.of(totalsKey, inflightTokenKey, inflightCostKey),
                            String.valueOf(prompt), String.valueOf(dailyTokenLimit),
                            String.valueOf(costMicros), String.valueOf(Math.round(dailyCostLimit * 1_000_000D)));
                    allowed = Long.valueOf(1L).equals(result);
                } catch (RuntimeException ex) {
                    // A configured distributed quota must fail closed. A
                    // process-local reservation is used only when Redis was
                    // never configured, not when it is failing.
                    allowed = false;
                }
            } else if (allowed && redis == null && (dailyTokenLimit > 0 || dailyCostLimit > 0D)) {
                localInflightTokens.computeIfAbsent(scope, ignored -> new LongAdder()).add(prompt);
                localInflightCost.computeIfAbsent(scope, ignored -> new DoubleAdder()).add(costEstimate);
                localReservation = true;
            }
            if (allowed) {
                reservation.set(new Reservation(scope, inflightTokenKey, inflightCostKey, prompt, costMicros, localReservation));
                if (redis != null && objectMapper != null) try { redis.opsForSet().add(key(SCOPES_KEY), scope); } catch (RuntimeException ignored) { }
            }
            if (!allowed) meterRegistry.counter("curmerce.agent.policy.denied", "reason", "daily-budget").increment();
            if (dailyTokenLimit > 0 && projectedTokens >= Math.round(dailyTokenLimit * 0.8D)) {
                meterRegistry.counter("curmerce.agent.quota.warning", "dimension", "tokens").increment();
            }
            if (dailyCostLimit > 0D && projectedCost >= dailyCostLimit * 0.8D) {
                meterRegistry.counter("curmerce.agent.quota.warning", "dimension", "cost").increment();
            }
            return allowed;
        }
    }

    /** Binds the authenticated principal to model calls made on this thread. */
    public void bindPrincipal(String value) { principal.set(value == null || value.isBlank() ? "anonymous" : value); }
    public void clearPrincipal() { principal.remove(); }
    public String currentPrincipal() { return principal.get() == null ? "anonymous" : principal.get(); }

    public Usage record(String provider, int promptTokens, int completionTokens, Duration latency, double cost) {
        return record(currentPrincipal(), provider, promptTokens, completionTokens, latency, cost);
    }

    public Usage record(String principal, String provider, int promptTokens, int completionTokens,
                        Duration latency, double cost) {
        releaseReservation();
        Usage usage = new Usage(Math.max(0, promptTokens), Math.max(0, completionTokens),
                Math.max(0, latency.toMillis()), Math.max(0D, cost), provider == null ? "unknown" : provider);
        last.set(usage);
        requests.increment(); this.promptTokens.add(usage.promptTokens()); this.completionTokens.add(usage.completionTokens()); this.cost.add(usage.cost());
        String scope = scope(principal, provider);
        localScopes.merge(scope, new Summary(1, usage.promptTokens(), usage.completionTokens(), usage.cost()),
                (left, right) -> new Summary(left.requests() + right.requests(), left.promptTokens() + right.promptTokens(),
                        left.completionTokens() + right.completionTokens(), left.cost() + right.cost()));
        if (jdbcArchive != null) try { jdbcArchive.append(principal, usage); }
        catch (RuntimeException ignored) {
            meterRegistry.counter("curmerce.agent.usage.archive_failures", "backend", "jdbc").increment();
        }
        if (redis != null && objectMapper != null) try {
            redis.opsForValue().set(key(REDIS_KEY), objectMapper.writeValueAsString(usage), java.time.Duration.ofDays(7));
            redis.opsForHash().increment(key(REDIS_KEY) + ":totals", "requests", 1D);
            redis.opsForHash().increment(key(REDIS_KEY) + ":totals", "promptTokens", usage.promptTokens());
            redis.opsForHash().increment(key(REDIS_KEY) + ":totals", "completionTokens", usage.completionTokens());
            redis.opsForHash().increment(key(REDIS_KEY) + ":totals", "costMicros", Math.round(usage.cost() * 1_000_000D));
            redis.expire(key(REDIS_KEY) + ":totals", java.time.Duration.ofDays(30));
            redis.opsForHash().increment(key(REDIS_KEY) + ":scope:" + scope, "requests", 1D);
            redis.opsForHash().increment(key(REDIS_KEY) + ":scope:" + scope, "promptTokens", usage.promptTokens());
            redis.opsForHash().increment(key(REDIS_KEY) + ":scope:" + scope, "completionTokens", usage.completionTokens());
            redis.opsForHash().increment(key(REDIS_KEY) + ":scope:" + scope, "costMicros", Math.round(usage.cost() * 1_000_000D));
            redis.expire(key(REDIS_KEY) + ":scope:" + scope, java.time.Duration.ofDays(30));
            redis.opsForSet().add(key(SCOPES_KEY), scope);
        } catch (Exception ignored) { }
        meterRegistry.counter("curmerce.agent.requests", "provider", usage.provider()).increment();
        meterRegistry.counter("curmerce.agent.tokens", "kind", "prompt").increment(usage.promptTokens());
        meterRegistry.counter("curmerce.agent.tokens", "kind", "completion").increment(usage.completionTokens());
        meterRegistry.counter("curmerce.agent.cost", "provider", usage.provider()).increment(usage.cost());
        meterRegistry.timer("curmerce.agent.latency", "provider", usage.provider()).record(latency);
        return usage;
    }

    public Usage latest() {
        if (redis != null && objectMapper != null) try {
            String value = redis.opsForValue().get(key(REDIS_KEY));
            if (value != null) return objectMapper.readValue(value, Usage.class);
        } catch (Exception ignored) { }
        return last.get();
    }

    public Summary summary() {
        if (redis != null && objectMapper != null) try {
            var values = redis.opsForHash().entries(key(REDIS_KEY) + ":totals");
            if (values != null && !values.isEmpty()) {
                return new Summary(longValue(values.get("requests")), longValue(values.get("promptTokens")),
                        longValue(values.get("completionTokens")), longValue(values.get("costMicros")) / 1_000_000D);
            }
        } catch (RuntimeException ignored) { }
        return new Summary(requests.sum(), promptTokens.sum(), completionTokens.sum(), cost.sum());
    }

    public AgentUsageJdbcArchive.Report report(java.time.Instant from, java.time.Instant to) {
        if (jdbcArchive == null) return new AgentUsageJdbcArchive.Report(0, 0, 0, 0D, from, to);
        try { return jdbcArchive.report(from, to); } catch (RuntimeException ignored) {
            meterRegistry.counter("curmerce.agent.usage.archive_failures", "backend", "jdbc").increment();
            return new AgentUsageJdbcArchive.Report(0, 0, 0, 0D, from, to);
        }
    }

    /** Returns the current daily bucket for one principal/model pair. */
    public Summary scopedSummary(String principal, String model) {
        return scopedSummary(scope(principal, model));
    }

    /** Returns redacted scope keys and usage totals for internal reporting. */
    public Map<String, Summary> scopeSummaries() {
        java.util.Set<String> scopes = new java.util.LinkedHashSet<>(localScopes.keySet());
        if (redis != null) {
            try {
                java.util.Set<String> remote = redis.opsForSet().members(key(SCOPES_KEY));
                if (remote != null) scopes.addAll(remote);
            } catch (RuntimeException ignored) { }
        }
        Map<String, Summary> result = new java.util.TreeMap<>();
        scopes.forEach(scope -> result.put(scope, scopedSummary(scope)));
        return java.util.Map.copyOf(result);
    }

    private Summary scopedSummary(String scope) {
        if (redis != null && objectMapper != null) try {
            var values = redis.opsForHash().entries(key(REDIS_KEY) + ":scope:" + scope);
            if (values != null && !values.isEmpty()) {
                return new Summary(longValue(values.get("requests")), longValue(values.get("promptTokens")),
                        longValue(values.get("completionTokens")), longValue(values.get("costMicros")) / 1_000_000D);
            }
        } catch (RuntimeException ignored) { }
        return localScopes.getOrDefault(scope, new Summary(0, 0, 0, 0));
    }

    private static long longValue(Object value) {
        if (value == null) return 0L;
        try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ex) { return 0L; }
    }

    private void releaseReservation() {
        Reservation value = reservation.get();
        reservation.remove();
        if (value == null) return;
        if (value.localReservation()) {
            LongAdder tokens = localInflightTokens.get(value.scope());
            if (tokens != null) tokens.add(-value.tokens());
            DoubleAdder costs = localInflightCost.get(value.scope());
            if (costs != null) costs.add(-value.costMicros() / 1_000_000D);
        } else if (redis != null) try {
            redis.opsForValue().increment(value.inflightTokenKey(), -value.tokens());
            redis.opsForValue().increment(value.inflightCostKey(), -value.costMicros());
        } catch (RuntimeException ignored) { }
    }

    /** Releases a pre-flight reservation when a model call is rejected before the adapter runs. */
    public void cancelReservation() { releaseReservation(); }

    private static String scope(String principal, String model) {
        String tenant = AgentRequestContext.tenantScope();
        String subject = AgentPrincipalHasher.hash(principal == null || principal.isBlank() ? "anonymous" : principal);
        String modelPart = (model == null || model.isBlank() ? "unknown" : model.trim()).replaceAll("[^A-Za-z0-9._:-]", "_");
        long day = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        return tenant + ":" + subject + ":" + modelPart + ":" + day;
    }

    private static String key(String base) { return AgentRequestContext.key(base); }

    public record Usage(int promptTokens, int completionTokens, long latencyMillis, double cost, String provider) { }
    public record Summary(long requests, long promptTokens, long completionTokens, double cost) { }
    private record Reservation(String scope, String inflightTokenKey, String inflightCostKey,
                               long tokens, long costMicros, boolean localReservation) { }
}

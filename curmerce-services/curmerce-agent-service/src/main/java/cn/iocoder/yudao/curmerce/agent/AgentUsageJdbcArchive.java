package cn.iocoder.yudao.curmerce.agent;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

/** Durable, prompt-free usage ledger for cost and capacity reporting. */
public final class AgentUsageJdbcArchive {
    private final JdbcTemplate jdbc;
    private final int retentionDays;

    public AgentUsageJdbcArchive(DataSource dataSource, int retentionDays) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.retentionDays = Math.max(1, Math.min(retentionDays, 3650));
    }

    public void ensureSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS agent_usage_event ("
                + "id BIGINT NOT NULL AUTO_INCREMENT, occurred_at DATETIME(3) NOT NULL, "
                + "tenant_hash VARCHAR(64) NOT NULL, principal_hash VARCHAR(64) NOT NULL, model_name VARCHAR(128) NOT NULL, "
                + "prompt_tokens INT NOT NULL, completion_tokens INT NOT NULL, "
                + "latency_ms BIGINT NOT NULL, cost DECIMAL(20,8) NOT NULL, PRIMARY KEY(id), "
                + "KEY idx_agent_usage_time (occurred_at), KEY idx_agent_usage_scope (tenant_hash, principal_hash, model_name, occurred_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    public void append(String principal, AgentUsageRecorder.Usage usage) {
        jdbc.update("INSERT INTO agent_usage_event(occurred_at, tenant_hash, principal_hash, model_name, prompt_tokens, completion_tokens, latency_ms, cost) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Instant.now(), AgentRequestContext.tenantScope(), AgentPrincipalHasher.hash(principal), usage.provider(), usage.promptTokens(),
                usage.completionTokens(), usage.latencyMillis(), usage.cost());
        purgeExpired();
    }

    /** Purges old rows even when the service receives no new model requests. */
    public int purgeExpired() {
        // Bind the cutoff instead of using vendor-specific DATE_SUB syntax. This
        // keeps the retention policy identical in MySQL and embedded test DBs.
        return jdbc.update("DELETE FROM agent_usage_event WHERE occurred_at < ?",
                Timestamp.from(Instant.now().minusSeconds(retentionDays * 86400L)));
    }

    public Report report(Instant from, Instant to) {
        Map<String, Object> value = jdbc.queryForMap("SELECT COUNT(*) requests, COALESCE(SUM(prompt_tokens),0) prompt_tokens, "
                        + "COALESCE(SUM(completion_tokens),0) completion_tokens, COALESCE(SUM(cost),0) cost "
                        + "FROM agent_usage_event WHERE tenant_hash = ? AND occurred_at >= ? AND occurred_at < ?",
                AgentRequestContext.tenantScope(), from, to);
        return new Report(longValue(value.get("requests")), longValue(value.get("prompt_tokens")),
                longValue(value.get("completion_tokens")), doubleValue(value.get("cost")), from, to);
    }

    private static long longValue(Object value) { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
    private static double doubleValue(Object value) { return value == null ? 0D : Double.parseDouble(String.valueOf(value)); }
    public record Report(long requests, long promptTokens, long completionTokens, double cost, Instant from, Instant to) { }
}

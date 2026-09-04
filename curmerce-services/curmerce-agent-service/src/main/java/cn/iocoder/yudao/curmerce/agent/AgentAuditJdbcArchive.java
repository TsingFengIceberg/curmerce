package cn.iocoder.yudao.curmerce.agent;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** SQL-backed audit archive with bounded retention. Prompt contents are never stored. */
public class AgentAuditJdbcArchive {
    private final JdbcTemplate jdbc;
    private final int retentionDays;

    public AgentAuditJdbcArchive(DataSource dataSource, int retentionDays) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.retentionDays = Math.max(1, Math.min(retentionDays, 3650));
    }

    @PostConstruct
    void ensureSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS agent_audit_event ("
                + "id BIGINT NOT NULL AUTO_INCREMENT, occurred_at DATETIME(3) NOT NULL, "
                + "tenant_hash VARCHAR(64) NOT NULL, principal_hash VARCHAR(64) NOT NULL, action VARCHAR(96) NOT NULL, "
                + "outcome VARCHAR(64) NOT NULL, PRIMARY KEY (id), "
                + "KEY idx_agent_audit_time (occurred_at), KEY idx_agent_audit_scope (tenant_hash, principal_hash, occurred_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    public void append(AgentAuditRecorder.Entry entry) {
        jdbc.update("INSERT INTO agent_audit_event (occurred_at, tenant_hash, principal_hash, action, outcome) VALUES (?, ?, ?, ?, ?)",
                entry.at(), entry.tenantHash(), entry.principalHash(), entry.action(), entry.outcome());
        purgeExpired();
    }

    /** Purges old rows even when the service receives no new Agent requests. */
    public int purgeExpired() {
        // Bind the cutoff instead of using vendor-specific DATE_SUB syntax. This
        // keeps the retention policy identical in MySQL and embedded test DBs.
        return jdbc.update("DELETE FROM agent_audit_event WHERE occurred_at < ?",
                Timestamp.from(Instant.now().minusSeconds(retentionDays * 86400L)));
    }

    public List<AgentAuditRecorder.Entry> recent(int limit) {
        int safeLimit = Math.min(100, Math.max(1, limit));
        return jdbc.query("SELECT occurred_at, tenant_hash, principal_hash, action, outcome FROM agent_audit_event "
                        + "WHERE tenant_hash = ? ORDER BY id DESC LIMIT ?", (rs, row) -> new AgentAuditRecorder.Entry(
                        rs.getTimestamp("occurred_at").toInstant(), rs.getString("tenant_hash"), rs.getString("principal_hash"),
                        rs.getString("action"), rs.getString("outcome")), AgentRequestContext.tenantScope(), safeLimit).reversed();
    }
}

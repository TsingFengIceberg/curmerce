package cn.iocoder.yudao.curmerce.agent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional durable rule catalog. Rules are versioned and replaced as one
 * snapshot so model answers never observe a partially updated policy set.
 */
public final class AgentRuleJdbcStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public AgentRuleJdbcStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public void ensureSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS agent_platform_rule ("
                + "tenant_hash VARCHAR(64) NOT NULL, rule_key VARCHAR(64) NOT NULL, rule_value VARCHAR(2000) NOT NULL, "
                + "version BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, "
                + "PRIMARY KEY (tenant_hash, rule_key), KEY idx_agent_rule_version (tenant_hash, version)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        jdbc.execute("CREATE TABLE IF NOT EXISTS agent_platform_rule_revision ("
                + "id BIGINT NOT NULL AUTO_INCREMENT, tenant_hash VARCHAR(64) NOT NULL, rule_key VARCHAR(64) NOT NULL, "
                + "rule_value VARCHAR(2000) NOT NULL, version BIGINT NOT NULL, updated_at DATETIME(3) NOT NULL, "
                + "PRIMARY KEY (id), KEY idx_agent_rule_revision_version (version, updated_at), "
                + "KEY idx_agent_rule_revision_scope (tenant_hash, version, updated_at), "
                + "KEY idx_agent_rule_revision_key (tenant_hash, rule_key, version)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    public Map<String, Object> current() {
        Map<String, Object> result = new LinkedHashMap<>();
        jdbc.query("SELECT rule_key, rule_value FROM agent_platform_rule WHERE tenant_hash = ? ORDER BY rule_key",
                ps -> ps.setString(1, tenantHash()), (RowCallbackHandler) rs ->
                result.put(rs.getString("rule_key"), parseValue(rs.getString("rule_key"), rs.getString("rule_value"))));
        return Map.copyOf(result);
    }

    public long version() {
        Long value = jdbc.queryForObject("SELECT COALESCE(MAX(version), 0) FROM agent_platform_rule WHERE tenant_hash = ?",
                Long.class, tenantHash());
        return value == null ? 0L : value;
    }

    public void replace(Map<String, Object> rules, long version) {
        if (rules == null || rules.isEmpty()) throw new IllegalArgumentException("规则不能为空");
        transaction.executeWithoutResult(status -> {
            Long currentValue = jdbc.query("SELECT version FROM agent_platform_rule WHERE tenant_hash = ? ORDER BY version DESC LIMIT 1 FOR UPDATE",
                    ps -> ps.setString(1, tenantHash()),
                    rs -> rs.next() ? rs.getLong(1) : 0L);
            long current = currentValue == null ? 0L : currentValue;
            long safeVersion = version <= 0L ? current + 1L : version;
            if (safeVersion <= current) throw new IllegalStateException("规则版本已过期，请基于最新版本更新");
            Map<String, Object> valid = new LinkedHashMap<>();
            rules.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && String.valueOf(value).length() <= 2000) {
                    valid.put(key.trim(), value);
                }
            });
            if (valid.isEmpty()) throw new IllegalArgumentException("规则不能为空");
            jdbc.update("DELETE FROM agent_platform_rule WHERE tenant_hash = ?", tenantHash());
            Instant updatedAt = Instant.now();
            for (Map.Entry<String, Object> entry : valid.entrySet()) {
                jdbc.update("INSERT INTO agent_platform_rule(tenant_hash, rule_key, rule_value, version, updated_at) VALUES (?, ?, ?, ?, ?)",
                        tenantHash(), entry.getKey(), String.valueOf(entry.getValue()), safeVersion, updatedAt);
                jdbc.update("INSERT INTO agent_platform_rule_revision(tenant_hash, rule_key, rule_value, version, updated_at) VALUES (?, ?, ?, ?, ?)",
                        tenantHash(), entry.getKey(), String.valueOf(entry.getValue()), safeVersion, updatedAt);
            }
        });
    }

    /**
     * Restores a complete historical snapshot as a new monotonic version.
     * The expected version is a compare-and-set guard for concurrent admins.
     */
    public long rollback(long targetVersion, long expectedCurrentVersion) {
        if (targetVersion <= 0L) throw new IllegalArgumentException("目标规则版本必须为正数");
        Long result = transaction.execute(status -> {
            Long currentValue = jdbc.query("SELECT version FROM agent_platform_rule WHERE tenant_hash = ? ORDER BY version DESC LIMIT 1 FOR UPDATE",
                    ps -> ps.setString(1, tenantHash()),
                    rs -> rs.next() ? rs.getLong(1) : 0L);
            long current = currentValue == null ? 0L : currentValue;
            if (expectedCurrentVersion > 0L && current != expectedCurrentVersion) {
                throw new IllegalStateException("规则版本已变化，请重新读取后再回滚");
            }
            if (targetVersion >= current) throw new IllegalArgumentException("只能回滚到更早的规则版本");
            List<Revision> snapshot = jdbc.query("SELECT rule_key, rule_value, version, updated_at "
                            + "FROM agent_platform_rule_revision WHERE tenant_hash = ? AND version = ? ORDER BY rule_key",
                    (rs, row) -> new Revision(rs.getString("rule_key"),
                            parseValue(rs.getString("rule_key"), rs.getString("rule_value")),
                            rs.getLong("version"), rs.getTimestamp("updated_at").toInstant()), tenantHash(), targetVersion);
            if (snapshot.isEmpty()) throw new IllegalArgumentException("目标规则版本不存在或没有完整快照");
            long nextVersion = current + 1L;
            Instant updatedAt = Instant.now();
            jdbc.update("DELETE FROM agent_platform_rule WHERE tenant_hash = ?", tenantHash());
            for (Revision entry : snapshot) {
                String value = String.valueOf(entry.value());
                jdbc.update("INSERT INTO agent_platform_rule(tenant_hash, rule_key, rule_value, version, updated_at) VALUES (?, ?, ?, ?, ?)",
                        tenantHash(), entry.key(), value, nextVersion, updatedAt);
                jdbc.update("INSERT INTO agent_platform_rule_revision(tenant_hash, rule_key, rule_value, version, updated_at) VALUES (?, ?, ?, ?, ?)",
                        tenantHash(), entry.key(), value, nextVersion, updatedAt);
            }
            return nextVersion;
        });
        return result == null ? 0L : result;
    }

    public List<Revision> history(int limit) {
        int safeLimit = Math.min(200, Math.max(1, limit));
        return jdbc.query("SELECT rule_key, rule_value, version, updated_at FROM agent_platform_rule_revision "
                        + "WHERE tenant_hash = ? ORDER BY version DESC, updated_at DESC LIMIT ?", (rs, row) -> new Revision(
                        rs.getString("rule_key"), parseValue(rs.getString("rule_key"), rs.getString("rule_value")),
                        rs.getLong("version"), rs.getTimestamp("updated_at").toInstant()), tenantHash(), safeLimit);
    }

    private String tenantHash() { return AgentRequestContext.tenantScope(); }

    private static Object parseValue(String key, String value) {
        if ("paymentTimeoutMinutes".equals(key)) {
            try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { }
        }
        return value;
    }

    public record Revision(String key, Object value, long version, Instant updatedAt) { }
}

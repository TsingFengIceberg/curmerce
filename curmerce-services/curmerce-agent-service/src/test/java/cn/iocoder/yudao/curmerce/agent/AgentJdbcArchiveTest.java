package cn.iocoder.yudao.curmerce.agent;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentJdbcArchiveTest {
    @Test
    void auditAndUsageArchivesPersistAndAggregateWithoutPromptContents() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:agent-archives;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        AgentAuditJdbcArchive audit = new AgentAuditJdbcArchive(dataSource, 30);
        audit.ensureSchema();
        audit.append(new AgentAuditRecorder.Entry(Instant.now(), "hash", "assist", "accepted"));
        assertEquals(1, audit.recent(10).size());

        AgentUsageJdbcArchive usage = new AgentUsageJdbcArchive(dataSource, 30);
        usage.ensureSchema();
        Instant now = Instant.now();
        usage.append("Bearer secret-value", new AgentUsageRecorder.Usage(10, 4, 12,
                0.25D, "test-model"));
        AgentUsageJdbcArchive.Report report = usage.report(now.minusSeconds(2), Instant.now().plusSeconds(2));
        assertEquals(1L, report.requests());
        assertEquals(10L, report.promptTokens());
        assertEquals(4L, report.completionTokens());
        assertEquals(0.25D, report.cost(), 0.000001D);
    }

    @Test
    void usageRecorderFallsBackWhenJdbcArchiveIsUnavailable() throws Exception {
        io.micrometer.core.instrument.simple.SimpleMeterRegistry metrics = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        AgentUsageRecorder recorder = new AgentUsageRecorder(metrics);
        AgentUsageJdbcArchive archive = new AgentUsageJdbcArchive(new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                throw new SQLException("database down");
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                throw new SQLException("database down");
            }
        }, 30);
        java.lang.reflect.Field field = AgentUsageRecorder.class.getDeclaredField("jdbcArchive");
        field.setAccessible(true);
        field.set(recorder, archive);

        recorder.record("user", "model", 2, 1, Duration.ofMillis(5), 0D);
        assertEquals(1L, recorder.summary().requests());
        assertEquals(1.0D, metrics.counter("curmerce.agent.usage.archive_failures", "backend", "jdbc").count());
    }
}

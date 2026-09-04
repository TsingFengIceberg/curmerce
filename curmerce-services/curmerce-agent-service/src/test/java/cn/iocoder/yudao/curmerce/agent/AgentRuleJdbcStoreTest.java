package cn.iocoder.yudao.curmerce.agent;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentRuleJdbcStoreTest {
    @Test
    void rollbackRestoresHistoricalSnapshotAsNewVersion() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:agent-rules;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        AgentRuleJdbcStore store = new AgentRuleJdbcStore(dataSource);
        store.ensureSchema();

        store.replace(Map.of("paymentTimeoutMinutes", 30, "refundPolicy", "v1"), 0L);
        store.replace(Map.of("paymentTimeoutMinutes", 45, "refundPolicy", "v2"), 0L);

        assertEquals(3L, store.rollback(1L, 2L));
        assertEquals(30, store.current().get("paymentTimeoutMinutes"));
        assertEquals("v1", store.current().get("refundPolicy"));
        assertEquals(3L, store.version());
        assertThrows(IllegalStateException.class, () -> store.rollback(1L, 2L));
    }
}

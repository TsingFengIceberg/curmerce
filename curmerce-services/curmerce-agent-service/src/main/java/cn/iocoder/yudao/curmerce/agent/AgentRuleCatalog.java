package cn.iocoder.yudao.curmerce.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime rule catalog so the Agent does not hard-code policy text in tools. */
@Component
public class AgentRuleCatalog {
    private final AgentRuleJdbcStore jdbcStore;
    @Value("${curmerce.agent.rules.payment-timeout-minutes:30}")
    private int paymentTimeoutMinutes;
    @Value("${curmerce.agent.rules.refund-policy:已发货订单需商家审核，退款状态异步更新}")
    private String refundPolicy;
    @Value("${curmerce.agent.rules.stock-source:MySQL 事务库存为最终事实来源}")
    private String stockSource;
    @Value("${curmerce.agent.rules.auction:出价按金额和入库顺序确定领先者}")
    private String auction;

    public AgentRuleCatalog() { this(null); }

    @org.springframework.beans.factory.annotation.Autowired
    public AgentRuleCatalog(org.springframework.beans.factory.ObjectProvider<AgentRuleJdbcStore> provider) {
        this.jdbcStore = provider.getIfAvailable();
    }

    public Map<String, Object> current() {
        if (jdbcStore != null) {
            try {
                Map<String, Object> persisted = jdbcStore.current();
                if (!persisted.isEmpty()) return persisted;
            } catch (RuntimeException ignored) {
                // YAML remains the safe startup fallback while the rule store recovers.
            }
        }
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("paymentTimeoutMinutes", Math.max(1, paymentTimeoutMinutes));
        rules.put("refundPolicy", refundPolicy);
        rules.put("stockSource", stockSource);
        rules.put("auction", auction);
        return rules;
    }

    public long version() {
        if (jdbcStore == null) return 0L;
        try { return jdbcStore.version(); } catch (RuntimeException ignored) { return 0L; }
    }

    public void replace(Map<String, Object> rules, long version) {
        if (jdbcStore == null) throw new IllegalStateException("规则数据库未启用");
        jdbcStore.replace(rules, version);
    }

    public long rollback(long targetVersion, long expectedCurrentVersion) {
        if (jdbcStore == null) throw new IllegalStateException("规则数据库未启用");
        return jdbcStore.rollback(targetVersion, expectedCurrentVersion);
    }

    public java.util.List<AgentRuleJdbcStore.Revision> history(int limit) {
        if (jdbcStore == null) return java.util.List.of();
        try { return jdbcStore.history(limit); } catch (RuntimeException ignored) { return java.util.List.of(); }
    }
}

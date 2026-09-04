-- Versioned Agent platform-rule snapshot. Apply before enabling JDBC rules.
CREATE TABLE IF NOT EXISTS agent_platform_rule (
  tenant_hash VARCHAR(64) NOT NULL,
  rule_key VARCHAR(64) NOT NULL,
  rule_value VARCHAR(2000) NOT NULL,
  version BIGINT NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (tenant_hash, rule_key),
  KEY idx_agent_rule_version (tenant_hash, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO agent_platform_rule(tenant_hash, rule_key, rule_value, version, updated_at)
SELECT 'default', 'paymentTimeoutMinutes', '30', 1, CURRENT_TIMESTAMP(3)
WHERE NOT EXISTS (SELECT 1 FROM agent_platform_rule WHERE tenant_hash='default' AND rule_key='paymentTimeoutMinutes');
INSERT INTO agent_platform_rule(tenant_hash, rule_key, rule_value, version, updated_at)
SELECT 'default', 'refundPolicy', '已发货订单需商家审核，退款状态异步更新', 1, CURRENT_TIMESTAMP(3)
WHERE NOT EXISTS (SELECT 1 FROM agent_platform_rule WHERE tenant_hash='default' AND rule_key='refundPolicy');
INSERT INTO agent_platform_rule(tenant_hash, rule_key, rule_value, version, updated_at)
SELECT 'default', 'stockSource', 'MySQL 事务库存为最终事实来源', 1, CURRENT_TIMESTAMP(3)
WHERE NOT EXISTS (SELECT 1 FROM agent_platform_rule WHERE tenant_hash='default' AND rule_key='stockSource');
INSERT INTO agent_platform_rule(tenant_hash, rule_key, rule_value, version, updated_at)
SELECT 'default', 'auction', '出价按金额和入库顺序确定领先者', 1, CURRENT_TIMESTAMP(3)
WHERE NOT EXISTS (SELECT 1 FROM agent_platform_rule WHERE tenant_hash='default' AND rule_key='auction');

CREATE TABLE IF NOT EXISTS agent_platform_rule_revision (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_hash VARCHAR(64) NOT NULL,
  rule_key VARCHAR(64) NOT NULL,
  rule_value VARCHAR(2000) NOT NULL,
  version BIGINT NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_agent_rule_revision_version (tenant_hash, version, updated_at),
  KEY idx_agent_rule_revision_key (tenant_hash, rule_key, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO agent_platform_rule_revision(tenant_hash, rule_key, rule_value, version, updated_at)
SELECT tenant_hash, rule_key, rule_value, version, updated_at FROM agent_platform_rule r
WHERE NOT EXISTS (SELECT 1 FROM agent_platform_rule_revision h WHERE h.tenant_hash=r.tenant_hash AND h.rule_key=r.rule_key AND h.version=r.version);

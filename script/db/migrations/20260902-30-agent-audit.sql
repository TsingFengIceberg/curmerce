-- Optional Agent audit archive. The Agent service creates this table lazily when
-- CURMERCE_AGENT_AUDIT_JDBC_ENABLED=true; this migration is provided for
-- environments that require schema review before enabling the feature.
CREATE TABLE IF NOT EXISTS agent_audit_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  occurred_at DATETIME(3) NOT NULL,
  tenant_hash VARCHAR(64) NOT NULL,
  principal_hash VARCHAR(64) NOT NULL,
  action VARCHAR(96) NOT NULL,
  outcome VARCHAR(64) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_agent_audit_time (occurred_at),
  KEY idx_agent_audit_scope (tenant_hash, principal_hash, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_usage_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  occurred_at DATETIME(3) NOT NULL,
  tenant_hash VARCHAR(64) NOT NULL,
  principal_hash VARCHAR(64) NOT NULL,
  model_name VARCHAR(128) NOT NULL,
  prompt_tokens INT NOT NULL,
  completion_tokens INT NOT NULL,
  latency_ms BIGINT NOT NULL,
  cost DECIMAL(20,8) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_agent_usage_time (occurred_at),
  KEY idx_agent_usage_scope (tenant_hash, principal_hash, model_name, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

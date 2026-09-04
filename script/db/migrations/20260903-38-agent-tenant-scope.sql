-- Add tenant namespaces to Agent durable stores for installations that applied
-- migrations 30/33 before tenant-aware Agent storage was introduced.
-- Existing rows are retained in the explicit "default" namespace and must be
-- reviewed before enabling multiple tenants.
SET NAMES utf8mb4;

SET @db = DATABASE();

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='agent_audit_event' AND column_name='tenant_hash') = 0,
  'ALTER TABLE agent_audit_event ADD COLUMN tenant_hash VARCHAR(64) NOT NULL DEFAULT ''default'' AFTER occurred_at', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=@db AND table_name='agent_audit_event' AND index_name='idx_agent_audit_scope') = 0,
  'ALTER TABLE agent_audit_event ADD KEY idx_agent_audit_scope (tenant_hash, principal_hash, occurred_at)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='agent_usage_event' AND column_name='tenant_hash') = 0,
  'ALTER TABLE agent_usage_event ADD COLUMN tenant_hash VARCHAR(64) NOT NULL DEFAULT ''default'' AFTER occurred_at', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=@db AND table_name='agent_usage_event' AND index_name='idx_agent_usage_scope') = 0,
  'ALTER TABLE agent_usage_event ADD KEY idx_agent_usage_scope (tenant_hash, principal_hash, model_name, occurred_at)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='agent_platform_rule' AND column_name='tenant_hash') = 0,
  'ALTER TABLE agent_platform_rule ADD COLUMN tenant_hash VARCHAR(64) NOT NULL DEFAULT ''default'' FIRST', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=@db AND table_name='agent_platform_rule' AND index_name='PRIMARY') = 1,
  'ALTER TABLE agent_platform_rule DROP PRIMARY KEY, ADD PRIMARY KEY (tenant_hash, rule_key)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db AND table_name='agent_platform_rule_revision' AND column_name='tenant_hash') = 0,
  'ALTER TABLE agent_platform_rule_revision ADD COLUMN tenant_hash VARCHAR(64) NOT NULL DEFAULT ''default'' AFTER id', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=@db AND table_name='agent_platform_rule_revision' AND index_name='idx_agent_rule_revision_scope') = 0,
  'ALTER TABLE agent_platform_rule_revision ADD KEY idx_agent_rule_revision_scope (tenant_hash, version, updated_at)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE s;

SELECT table_name, column_name, data_type, column_default
FROM information_schema.columns
WHERE table_schema=@db AND table_name IN ('agent_audit_event','agent_usage_event','agent_platform_rule','agent_platform_rule_revision')
  AND column_name='tenant_hash'
ORDER BY table_name;

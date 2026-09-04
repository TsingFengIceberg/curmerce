-- Rollback only the optional Agent audit archive.
DROP TABLE IF EXISTS agent_audit_event;
DROP TABLE IF EXISTS agent_usage_event;

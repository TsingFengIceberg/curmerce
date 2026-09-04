-- Roll back only when the Agent service is stopped and the current rules have
-- been exported. The revision table is dropped first because it is the audit
-- history for the current snapshot.
DROP TABLE IF EXISTS agent_platform_rule_revision;
DROP TABLE IF EXISTS agent_platform_rule;

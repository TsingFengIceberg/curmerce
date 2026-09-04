-- Rollback is only safe after all tenant-scoped events have been drained or
-- exported. It intentionally refuses to discard tenant ownership silently.
SET NAMES utf8mb4;

SELECT table_name, COUNT(*) AS non_default_rows
FROM (
  SELECT 'commerce_outbox_event' AS table_name FROM commerce_outbox_event WHERE tenant_id <> 'default' AND deleted=b'0'
  UNION ALL
  SELECT 'community_search_outbox' AS table_name FROM community_search_outbox WHERE tenant_id <> 'default' AND deleted=b'0'
) rows_to_check
GROUP BY table_name;

-- The caller must explicitly remove non-default rows before applying the
-- destructive compatibility rollback below.
SET @commerce_non_default = (SELECT COUNT(*) FROM commerce_outbox_event WHERE tenant_id <> 'default' AND deleted=b'0');
SET @community_non_default = (SELECT COUNT(*) FROM community_search_outbox WHERE tenant_id <> 'default' AND deleted=b'0');
SELECT IF(@commerce_non_default = 0 AND @community_non_default = 0,
          'safe to remove tenant envelope',
          'ABORT: non-default tenant events remain') AS rollback_check;

-- This script is deliberately diagnostic-only. Drop the tenant columns only
-- after a reviewed, environment-specific compatibility procedure has drained
-- all non-default rows and restored the original unique indexes.

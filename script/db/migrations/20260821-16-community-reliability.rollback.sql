ALTER TABLE community_report
  DROP INDEX uk_community_report_pending,
  DROP COLUMN pending_key;

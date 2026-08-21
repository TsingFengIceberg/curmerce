-- Community reliability: one active pending report per reporter and post.
ALTER TABLE community_report
  ADD COLUMN pending_key TINYINT GENERATED ALWAYS AS (IF(status = 0 AND deleted = b'0', 1, NULL)) STORED,
  ADD UNIQUE KEY uk_community_report_pending (post_id, reporter_user_id, pending_key);

-- Rollback for 20260819-12-commerce-outbox.sql.
-- Only run after outbox events and reconciliation issues have been exported or
-- intentionally removed; dropping the tables loses the audit trail.
SET NAMES utf8mb4;

SELECT COUNT(*) AS pending_outbox_count
FROM commerce_outbox_event
WHERE status IN (10, 30);
SELECT COUNT(*) AS open_reconciliation_issue_count
FROM commerce_reconciliation_issue
WHERE status = 10;

-- DROP TABLE commerce_reconciliation_issue;
-- DROP TABLE commerce_outbox_event;

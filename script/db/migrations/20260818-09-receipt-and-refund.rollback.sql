-- Rollback for 20260818-09-receipt-and-refund.sql.
-- Only run after all refund records have been exported or removed.
SET NAMES utf8mb4;

SELECT COUNT(*) AS existing_refund_count FROM commerce_refund;
SELECT COUNT(*) AS completed_orders_with_completion_time
FROM commerce_order
WHERE completion_time IS NOT NULL;

DROP TABLE commerce_refund;
ALTER TABLE commerce_order DROP COLUMN completion_time;

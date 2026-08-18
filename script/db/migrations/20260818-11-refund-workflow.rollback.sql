-- Rollback for 20260818-11-refund-workflow.sql.
-- Only run after refund review/callback data has been exported or intentionally removed.
SET NAMES utf8mb4;

SELECT COUNT(*) AS terminal_refund_count
FROM commerce_refund
WHERE status IN (30, 40, 50);

ALTER TABLE commerce_refund
  DROP CHECK chk_commerce_refund_callback,
  DROP CHECK chk_commerce_refund_processed_time,
  DROP CHECK chk_commerce_refund_review_time,
  DROP CHECK chk_commerce_refund_status,
  ADD CONSTRAINT chk_commerce_refund_status CHECK (status IN (10, 20, 30, 40)),
  ADD CONSTRAINT chk_commerce_refund_processed_time CHECK (
    (status IN (10, 20) AND processed_time IS NULL)
    OR (status IN (30, 40) AND processed_time IS NOT NULL)
  ),
  DROP COLUMN callback_success,
  DROP COLUMN callback_id,
  DROP COLUMN review_remark,
  DROP COLUMN reviewed_time,
  DROP COLUMN reviewer_user_id;

ALTER TABLE commerce_order
  DROP CHECK chk_commerce_order_refund_status,
  DROP KEY idx_commerce_order_member_refund_status_time,
  DROP COLUMN refund_status;

DELETE m FROM system_menu m
WHERE m.deleted = b'0'
  AND m.permission IN ('commerce:refund:query', 'commerce:refund:audit', 'commerce:refund:callback',
                       'commerce:refund:self-query', 'commerce:refund:self-audit');

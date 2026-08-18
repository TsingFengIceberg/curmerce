-- Curmerce refund review, callback idempotency, and buyer order after-sale status.
-- Depends on 20260818-09-receipt-and-refund.sql. MySQL 8.0.16+ is required.
-- Review the preflight results before applying.
SET NAMES utf8mb4;

SELECT VERSION() AS mysql_version;
SELECT status, COUNT(*) AS order_count
FROM commerce_order
GROUP BY status
ORDER BY status;
SELECT status, COUNT(*) AS refund_count
FROM commerce_refund
GROUP BY status
ORDER BY status;

ALTER TABLE commerce_order
  ADD COLUMN refund_status TINYINT NOT NULL DEFAULT 0 AFTER status,
  ADD KEY idx_commerce_order_member_refund_status_time
    (member_user_id, refund_status, create_time, id),
  ADD CONSTRAINT chk_commerce_order_refund_status
    CHECK (refund_status IN (0, 10, 20, 30, 40, 50));

ALTER TABLE commerce_refund
  ADD COLUMN reviewer_user_id BIGINT NULL AFTER requested_time,
  ADD COLUMN reviewed_time DATETIME NULL AFTER reviewer_user_id,
  ADD COLUMN review_remark VARCHAR(255) NULL AFTER reviewed_time,
  ADD COLUMN callback_id VARCHAR(64) NULL AFTER review_remark,
  ADD COLUMN callback_success BIT NULL AFTER callback_id,
  DROP CHECK chk_commerce_refund_status,
  DROP CHECK chk_commerce_refund_processed_time;

SELECT COUNT(*) AS legacy_order_refund_status_mismatch
FROM commerce_order o
JOIN commerce_refund r ON r.order_id = o.id
WHERE o.refund_status IS NULL OR o.refund_status <> r.status;

SELECT COUNT(*) AS legacy_terminal_without_review_metadata
FROM commerce_refund
WHERE status IN (20, 30, 40)
  AND (reviewer_user_id IS NULL OR reviewed_time IS NULL);
SELECT COUNT(*) AS legacy_success_without_callback_metadata
FROM commerce_refund
WHERE status = 30
  AND (callback_id IS NULL OR callback_success IS NULL);

-- Legacy rows created before the review/callback workflow have no actor or
-- provider callback identifier. Preserve them as historical records instead
-- of making the constraint migration fail. Reviewer id 0 and the legacy
-- callback prefix explicitly mean "unknown historical actor/provider"; new
-- application writes must always provide real values.
UPDATE commerce_refund
SET reviewer_user_id = COALESCE(reviewer_user_id, 0),
    reviewed_time = COALESCE(reviewed_time, processed_time, requested_time, CURRENT_TIMESTAMP),
    review_remark = COALESCE(review_remark, 'Migrated legacy refund record')
WHERE status IN (20, 30, 40)
  AND (reviewer_user_id IS NULL OR reviewed_time IS NULL);

UPDATE commerce_refund
SET processed_time = COALESCE(processed_time, reviewed_time, requested_time, CURRENT_TIMESTAMP)
WHERE status IN (30, 40)
  AND processed_time IS NULL;

UPDATE commerce_refund
SET callback_id = COALESCE(callback_id, CONCAT('legacy-refund-', id)),
    callback_success = COALESCE(callback_success, b'1')
WHERE status = 30
  AND (callback_id IS NULL OR callback_success IS NULL);

-- Keep the new order-level after-sale projection consistent with historical
-- refund rows. Each order has at most one refund by uk_commerce_refund_order.
UPDATE commerce_order o
JOIN commerce_refund r ON r.order_id = o.id
SET o.refund_status = r.status
WHERE o.refund_status IS NULL OR o.refund_status <> r.status;

ALTER TABLE commerce_refund
  ADD CONSTRAINT chk_commerce_refund_status CHECK (status IN (10, 20, 30, 40, 50)),
  ADD CONSTRAINT chk_commerce_refund_review_time CHECK (
    (status = 10 AND reviewed_time IS NULL AND reviewer_user_id IS NULL)
    OR (status IN (20, 30, 40, 50) AND reviewed_time IS NOT NULL AND reviewer_user_id IS NOT NULL)
  ),
  ADD CONSTRAINT chk_commerce_refund_processed_time CHECK (
    (status IN (10, 20) AND processed_time IS NULL)
    OR (status IN (30, 40, 50) AND processed_time IS NOT NULL)
  ),
  ADD CONSTRAINT chk_commerce_refund_callback CHECK (
    (status IN (10, 20, 40) AND callback_id IS NULL AND callback_success IS NULL)
    OR (status IN (30, 50) AND callback_id IS NOT NULL AND callback_success IS NOT NULL)
  );

INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, status, visible,
   keep_alive, always_show, creator, updater, deleted)
SELECT v.name, v.permission, 3, v.sort, 0, '', '#', '', 0, b'1', b'1', b'1',
       'migration', 'migration', b'0'
FROM (
    SELECT 'Refund query' AS name, 'commerce:refund:query' AS permission, 50 AS sort
    UNION ALL SELECT 'Refund audit', 'commerce:refund:audit', 51
    UNION ALL SELECT 'Refund callback', 'commerce:refund:callback', 52
    UNION ALL SELECT 'Own merchant refund query', 'commerce:refund:self-query', 53
    UNION ALL SELECT 'Own merchant refund audit', 'commerce:refund:self-audit', 54
) v
WHERE NOT EXISTS (
    SELECT 1 FROM system_menu m
    WHERE m.permission = v.permission AND m.deleted = b'0'
);

INSERT INTO system_role_menu (role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT r.id, m.id, 'migration', 'migration', b'0', 0
FROM system_role r
JOIN system_menu m ON m.permission IN ('commerce:refund:self-query', 'commerce:refund:self-audit')
WHERE r.code = 'merchant_owner' AND r.tenant_id = 0 AND r.deleted = b'0'
  AND m.deleted = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = b'0'
  );

SELECT permission, COUNT(*) AS permission_count
FROM system_menu
WHERE deleted = b'0'
  AND permission IN ('commerce:refund:query', 'commerce:refund:audit', 'commerce:refund:callback',
                     'commerce:refund:self-query', 'commerce:refund:self-audit')
GROUP BY permission ORDER BY permission;
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name IN ('commerce_order', 'commerce_refund')
  AND column_name IN ('refund_status', 'reviewer_user_id', 'reviewed_time', 'review_remark',
                      'callback_id', 'callback_success')
ORDER BY table_name, ordinal_position;

-- Rollback for 20260818-08-order-fulfillment.sql.
-- Only run after all orders in states 30/40 have been migrated or removed.
SET NAMES utf8mb4;

SELECT COUNT(*) AS non_rollback_order_status_count
FROM commerce_order
WHERE status NOT IN (10, 20);

ALTER TABLE commerce_order
  DROP KEY idx_commerce_order_merchant_store_status_time,
  DROP CHECK chk_commerce_order_status,
  ADD CONSTRAINT chk_commerce_order_status CHECK (status IN (10, 20)),
  DROP COLUMN tracking_no,
  DROP COLUMN logistics_company,
  DROP COLUMN shipping_time;

DELETE rm FROM system_role_menu rm
JOIN system_role r ON r.id = rm.role_id
JOIN system_menu m ON m.id = rm.menu_id
WHERE r.code = 'merchant_owner' AND r.tenant_id = 0
  AND m.permission IN ('commerce:order:self-query', 'commerce:order:self-ship');

DELETE FROM system_menu
WHERE permission IN ('commerce:order:self-query', 'commerce:order:self-ship')
  AND deleted = b'0';

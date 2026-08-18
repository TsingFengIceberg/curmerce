-- Curmerce merchant order fulfillment: shipped/completed states, shipment snapshot,
-- merchant-scoped pending-shipment lookup, and merchant self-service permissions.
-- MySQL 8.0.16+ is required. Review the preflight results before applying.
SET NAMES utf8mb4;

SELECT VERSION() AS mysql_version;
SELECT COUNT(*) AS invalid_existing_order_status_count
FROM commerce_order
WHERE status NOT IN (10, 20);

ALTER TABLE commerce_order
  ADD COLUMN shipping_time DATETIME NULL AFTER receiver_detail_address,
  ADD COLUMN logistics_company VARCHAR(64) NULL AFTER shipping_time,
  ADD COLUMN tracking_no VARCHAR(64) NULL AFTER logistics_company,
  DROP CHECK chk_commerce_order_status,
  ADD CONSTRAINT chk_commerce_order_status CHECK (status IN (10, 20, 30, 40)),
  ADD KEY idx_commerce_order_merchant_store_status_time
    (merchant_id, store_id, status, create_time, id);

INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, status, visible,
   keep_alive, always_show, creator, updater, deleted)
SELECT v.name, v.permission, 3, v.sort, 0, '', '#', '', 0, b'1', b'1', b'1',
       'migration', 'migration', b'0'
FROM (
    SELECT 'Own pending-shipment order query' AS name, 'commerce:order:self-query' AS permission, 40 AS sort
    UNION ALL SELECT 'Own order ship', 'commerce:order:self-ship', 41
) v
WHERE NOT EXISTS (
    SELECT 1 FROM system_menu m
    WHERE m.permission = v.permission AND m.deleted = b'0'
);

INSERT INTO system_role_menu (role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT r.id, m.id, 'migration', 'migration', b'0', 0
FROM system_role r
JOIN system_menu m ON m.permission IN ('commerce:order:self-query', 'commerce:order:self-ship')
WHERE r.code = 'merchant_owner' AND r.tenant_id = 0 AND r.deleted = b'0'
  AND m.deleted = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = b'0'
  );

-- Post-apply checks.
SELECT permission, COUNT(*) AS permission_count
FROM system_menu
WHERE deleted = b'0'
  AND permission IN ('commerce:order:self-query', 'commerce:order:self-ship')
GROUP BY permission ORDER BY permission;
SELECT COUNT(*) AS merchant_owner_order_permission_count
FROM system_role_menu rm
JOIN system_role r ON r.id = rm.role_id
JOIN system_menu m ON m.id = rm.menu_id
WHERE r.code = 'merchant_owner' AND r.tenant_id = 0 AND r.deleted = b'0'
  AND rm.deleted = b'0'
  AND m.permission IN ('commerce:order:self-query', 'commerce:order:self-ship')
  AND m.deleted = b'0';
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'commerce_order'
  AND column_name IN ('shipping_time', 'logistics_company', 'tracking_no')
ORDER BY ordinal_position;

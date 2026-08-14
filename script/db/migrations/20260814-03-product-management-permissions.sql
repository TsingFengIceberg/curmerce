-- Curmerce product category, merchant product, and product review permissions.
-- Run the preflight queries first. Any duplicate active permission or multiple
-- active tenant-0 merchant_owner roles is a stop condition; this migration does
-- not repair existing System data.
SET NAMES utf8mb4;

SELECT permission, COUNT(*) AS active_count
FROM system_menu
WHERE deleted = b'0'
  AND permission IN (
    'commerce:product-category:create', 'commerce:product-category:update',
    'commerce:product-category:query', 'commerce:product:self-create',
    'commerce:product:self-update', 'commerce:product:self-query',
    'commerce:product:self-submit', 'commerce:product:self-publish',
    'commerce:product:query', 'commerce:product:audit'
  )
GROUP BY permission
HAVING COUNT(*) > 1;
SELECT code, COUNT(*) AS active_count
FROM system_role
WHERE tenant_id = 0 AND deleted = b'0' AND code = 'merchant_owner'
GROUP BY code
HAVING COUNT(*) > 1;

INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, status, visible,
   keep_alive, always_show, creator, updater, deleted)
SELECT v.name, v.permission, 3, v.sort, 0, '', '#', '', 0, b'1', b'1', b'1',
       'migration', 'migration', b'0'
FROM (
    SELECT 'Product category create' AS name, 'commerce:product-category:create' AS permission, 10 AS sort
    UNION ALL SELECT 'Product category update', 'commerce:product-category:update', 11
    UNION ALL SELECT 'Product category query', 'commerce:product-category:query', 12
    UNION ALL SELECT 'Own product create', 'commerce:product:self-create', 20
    UNION ALL SELECT 'Own product update', 'commerce:product:self-update', 21
    UNION ALL SELECT 'Own product query', 'commerce:product:self-query', 22
    UNION ALL SELECT 'Own product submit', 'commerce:product:self-submit', 23
    UNION ALL SELECT 'Own product publish', 'commerce:product:self-publish', 24
    UNION ALL SELECT 'Product query', 'commerce:product:query', 30
    UNION ALL SELECT 'Product audit', 'commerce:product:audit', 31
) v
WHERE NOT EXISTS (
    SELECT 1 FROM system_menu m
    WHERE m.permission = v.permission AND m.deleted = b'0'
);

INSERT INTO system_role_menu (role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT r.id, m.id, 'migration', 'migration', b'0', 0
FROM system_role r
JOIN system_menu m ON m.permission IN (
    'commerce:product-category:query', 'commerce:product:self-create',
    'commerce:product:self-update', 'commerce:product:self-query',
    'commerce:product:self-submit', 'commerce:product:self-publish'
)
WHERE r.code = 'merchant_owner' AND r.tenant_id = 0 AND r.deleted = b'0'
  AND m.deleted = b'0'
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu rm
      WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = b'0'
  );

-- Post-apply checks: ten active menus and exactly six active merchant grants.
SELECT permission, COUNT(*) AS permission_count
FROM system_menu
WHERE deleted = b'0'
  AND permission IN (
    'commerce:product-category:create', 'commerce:product-category:update',
    'commerce:product-category:query', 'commerce:product:self-create',
    'commerce:product:self-update', 'commerce:product:self-query',
    'commerce:product:self-submit', 'commerce:product:self-publish',
    'commerce:product:query', 'commerce:product:audit'
  )
GROUP BY permission ORDER BY permission;
SELECT COUNT(*) AS merchant_owner_product_self_permission_count
FROM system_role_menu rm
JOIN system_role r ON r.id = rm.role_id
JOIN system_menu m ON m.id = rm.menu_id
WHERE r.code = 'merchant_owner' AND r.tenant_id = 0 AND r.deleted = b'0'
  AND rm.deleted = b'0'
  AND m.permission IN (
    'commerce:product-category:query', 'commerce:product:self-create',
    'commerce:product:self-update', 'commerce:product:self-query',
    'commerce:product:self-submit', 'commerce:product:self-publish'
  );

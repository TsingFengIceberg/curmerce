-- Disposable local rollback aid. Do not run without explicit approval and a
-- backup. It removes only rows created by this migration and never business
-- data, users, roles, or product tables.
DELETE rm
FROM system_role_menu rm
JOIN system_role r ON r.id = rm.role_id
JOIN system_menu m ON m.id = rm.menu_id
WHERE r.code = 'merchant_owner' AND r.tenant_id = 0
  AND rm.creator = 'migration'
  AND m.creator = 'migration'
  AND m.permission IN (
    'commerce:product-category:create', 'commerce:product-category:update',
    'commerce:product-category:query', 'commerce:product:self-create',
    'commerce:product:self-update', 'commerce:product:self-query',
    'commerce:product:self-submit', 'commerce:product:self-publish',
    'commerce:product:query', 'commerce:product:audit'
  );

DELETE FROM system_menu
WHERE creator = 'migration' AND updater = 'migration' AND deleted = b'0'
  AND permission IN (
    'commerce:product-category:create', 'commerce:product-category:update',
    'commerce:product-category:query', 'commerce:product:self-create',
    'commerce:product:self-update', 'commerce:product:self-query',
    'commerce:product:self-submit', 'commerce:product:self-publish',
    'commerce:product:query', 'commerce:product:audit'
  )
  AND NOT EXISTS (
      SELECT 1 FROM system_role_menu rm WHERE rm.menu_id = system_menu.id AND rm.deleted = b'0'
  );

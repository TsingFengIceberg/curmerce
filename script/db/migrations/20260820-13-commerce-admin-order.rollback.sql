SET NAMES utf8mb4;

DELETE rm FROM system_role_menu rm
JOIN system_role r ON r.id = rm.role_id
JOIN system_menu m ON m.id = rm.menu_id
WHERE r.code = 'super_admin' AND r.tenant_id = 0
  AND m.permission = 'commerce:order:query'
  AND rm.creator = 'migration' AND rm.updater = 'migration';

DELETE FROM system_menu
WHERE permission = 'commerce:order:query'
  AND creator = 'migration' AND updater = 'migration';

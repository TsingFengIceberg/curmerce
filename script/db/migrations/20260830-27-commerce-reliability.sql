-- Curmerce reliability operator permissions and idempotent menu bootstrap.
SET NAMES utf8mb4;

INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, status, visible, keep_alive, always_show, creator, updater, deleted)
SELECT v.name, v.permission, 3, v.sort, 0, '', '#', '', 0, b'1', b'1', b'1', 'migration', 'migration', b'0'
FROM (
  SELECT 'Commerce reliability query' name, 'commerce:reliability:query' permission, 80 sort
  UNION ALL SELECT 'Commerce reliability operate', 'commerce:reliability:operate', 81
) v WHERE NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission = v.permission AND m.deleted = b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT r.id, m.id, 'migration', 'migration', b'0', 0
FROM system_role r JOIN system_menu m ON m.permission IN ('commerce:reliability:query','commerce:reliability:operate')
WHERE r.code = 'platform_admin' AND r.tenant_id = 0 AND r.deleted = b'0' AND m.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = b'0');

SELECT permission FROM system_menu
WHERE permission IN ('commerce:reliability:query','commerce:reliability:operate') AND deleted = b'0';

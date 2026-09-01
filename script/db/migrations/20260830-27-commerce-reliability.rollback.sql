SET NAMES utf8mb4;
DELETE rm FROM system_role_menu rm JOIN system_menu m ON m.id = rm.menu_id
WHERE m.permission IN ('commerce:reliability:query','commerce:reliability:operate');
DELETE FROM system_menu
WHERE permission IN ('commerce:reliability:query','commerce:reliability:operate');

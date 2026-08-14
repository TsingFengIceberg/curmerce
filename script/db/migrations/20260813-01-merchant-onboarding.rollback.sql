-- Disposable local rollback aid. Do not run without explicit approval and a backup.
-- It intentionally refuses to delete business rows or System users.
DELETE rm FROM system_role_menu rm JOIN system_role r ON r.id = rm.role_id
JOIN system_menu m ON m.id = rm.menu_id
WHERE r.code = 'merchant_owner' AND m.permission IN ('commerce:store:self-query', 'commerce:store:self-update');
DELETE FROM system_menu WHERE permission IN ('commerce:merchant:create', 'commerce:merchant:query', 'commerce:merchant:audit',
                                              'commerce:store:self-query', 'commerce:store:self-update')
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.menu_id = system_menu.id);
DELETE FROM system_role WHERE code = 'merchant_owner' AND NOT EXISTS
  (SELECT 1 FROM system_user_role ur WHERE ur.role_id = system_role.id AND ur.deleted = b'0');
-- Drop only after proving the tables contain no retained data.
-- DROP TABLE commerce_merchant_operator, commerce_store, commerce_merchant;
-- ALTER TABLE system_users DROP INDEX uk_system_users_tenant_active_username, DROP COLUMN active_username;
-- ALTER TABLE system_role DROP INDEX uk_system_role_tenant_active_code, DROP COLUMN active_code;

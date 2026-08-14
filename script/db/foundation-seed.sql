SET NAMES utf8mb4;

INSERT INTO system_dept
  (id, name, parent_id, sort, leader_user_id, phone, email, status, creator, updater, deleted, tenant_id)
VALUES
  (1, 'Curmerce', 0, 0, NULL, NULL, NULL, 0, 'bootstrap', 'bootstrap', b'0', 0);

INSERT INTO system_users
  (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status,
   login_ip, login_date, creator, updater, deleted, tenant_id)
VALUES
  (1, 'admin', '__CURMERCE_ADMIN_PASSWORD_HASH__', 'Curmerce Administrator', 'Local bootstrap administrator',
   1, '[]', '', '', 0, NULL, 0, '', NULL, 'bootstrap', 'bootstrap', b'0', 0);

INSERT INTO system_role
  (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, updater, deleted, tenant_id)
VALUES
  (1, 'Super Administrator', 'super_admin', 1, 1, '', 0, 1, 'Curmerce platform administrator',
   'bootstrap', 'bootstrap', b'0', 0);

INSERT INTO system_user_role
  (id, user_id, role_id, creator, updater, deleted, tenant_id)
VALUES
  (1, 1, 1, 'bootstrap', 'bootstrap', b'0', 0);

INSERT INTO system_oauth2_client
  (id, client_id, secret, name, logo, description, status, access_token_validity_seconds,
   refresh_token_validity_seconds, redirect_uris, authorized_grant_types, scopes, auto_approve_scopes,
   authorities, resource_ids, additional_information, creator, updater, deleted)
VALUES
  (1, 'default', '__CURMERCE_OAUTH_CLIENT_SECRET__', 'Curmerce Local Client', '',
   'Local first-party client used by administrator login', 0, 1800, 2592000, '[]',
   '["password","refresh_token"]', '[]', '[]', '[]', '[]', '{}', 'bootstrap', 'bootstrap', b'0');

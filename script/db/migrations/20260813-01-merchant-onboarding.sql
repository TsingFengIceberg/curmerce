-- Curmerce merchant onboarding and store ownership, v1.
-- Run the preflight SELECTs first. A non-empty result is a stop condition.
SET NAMES utf8mb4;

SELECT tenant_id, username, COUNT(*) AS active_count
FROM system_users WHERE deleted = b'0'
GROUP BY tenant_id, username HAVING COUNT(*) > 1;
SELECT tenant_id, code, COUNT(*) AS active_count
FROM system_role WHERE deleted = b'0'
GROUP BY tenant_id, code HAVING COUNT(*) > 1;

CREATE TABLE commerce_merchant (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  code VARCHAR(32) NOT NULL,
  contact_name VARCHAR(30) NOT NULL,
  contact_mobile VARCHAR(20) NOT NULL,
  default_store_name VARCHAR(64) NOT NULL,
  default_store_code VARCHAR(32) NOT NULL,
  status TINYINT NOT NULL,
  owner_user_id BIGINT NULL,
  reviewer_user_id BIGINT NULL,
  review_time DATETIME NULL,
  reject_reason VARCHAR(255) NULL,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_commerce_merchant_code (code),
  UNIQUE KEY uk_commerce_merchant_default_store_code (default_store_code),
  UNIQUE KEY uk_commerce_merchant_owner_user (owner_user_id),
  KEY idx_commerce_merchant_status_create (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Curmerce merchant review aggregate';

CREATE TABLE commerce_store (
  id BIGINT NOT NULL AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  code VARCHAR(32) NOT NULL,
  description VARCHAR(500) NULL,
  contact_name VARCHAR(30) NOT NULL,
  contact_mobile VARCHAR(20) NOT NULL,
  status TINYINT NOT NULL,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_commerce_store_merchant (merchant_id),
  UNIQUE KEY uk_commerce_store_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Curmerce merchant store';

CREATE TABLE commerce_merchant_operator (
  id BIGINT NOT NULL AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  operator_type TINYINT NOT NULL,
  status TINYINT NOT NULL,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_commerce_operator_merchant_user (merchant_id, user_id),
  UNIQUE KEY uk_commerce_operator_user (user_id),
  KEY idx_commerce_operator_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Curmerce merchant operator relation';

-- Soft-deleted System identities may be reused; active identities are unique per tenant.
ALTER TABLE system_users
  ADD COLUMN active_username VARCHAR(30) GENERATED ALWAYS AS (IF(deleted = b'0', username, NULL)) STORED,
  ADD UNIQUE KEY uk_system_users_tenant_active_username (tenant_id, active_username);
ALTER TABLE system_role
  ADD COLUMN active_code VARCHAR(100) GENERATED ALWAYS AS (IF(deleted = b'0', code, NULL)) STORED,
  ADD UNIQUE KEY uk_system_role_tenant_active_code (tenant_id, active_code);

INSERT INTO system_role
  (name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, updater, deleted, tenant_id)
SELECT 'Merchant Owner', 'merchant_owner', 90, 5, '', 0, 2, 'Curmerce merchant self-service role', 'migration', 'migration', b'0', 0
WHERE NOT EXISTS (SELECT 1 FROM system_role WHERE code = 'merchant_owner' AND tenant_id = 0 AND deleted = b'0');

INSERT INTO system_menu
  (name, permission, type, sort, parent_id, path, icon, component, status, visible, keep_alive, always_show, creator, updater, deleted)
SELECT v.name, v.permission, 3, v.sort, 0, '', '#', '', 0, b'1', b'1', b'1', 'migration', 'migration', b'0'
FROM (SELECT 'Merchant create' name, 'commerce:merchant:create' permission, 1 sort
      UNION ALL SELECT 'Merchant query', 'commerce:merchant:query', 2
      UNION ALL SELECT 'Merchant audit', 'commerce:merchant:audit', 3
      UNION ALL SELECT 'Own store query', 'commerce:store:self-query', 4
      UNION ALL SELECT 'Own store update', 'commerce:store:self-update', 5) v
WHERE NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission = v.permission AND m.deleted = b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT r.id, m.id, 'migration', 'migration', b'0', 0
FROM system_role r JOIN system_menu m
WHERE r.code = 'merchant_owner' AND r.tenant_id = 0 AND r.deleted = b'0'
  AND m.permission IN ('commerce:store:self-query', 'commerce:store:self-update') AND m.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = b'0');

-- Post-apply checks (all expected counts are one, except the role-menu count which is two).
SELECT COUNT(*) AS merchant_owner_count FROM system_role WHERE code = 'merchant_owner' AND tenant_id = 0 AND deleted = b'0';
SELECT permission, COUNT(*) AS permission_count FROM system_menu
WHERE permission IN ('commerce:merchant:create', 'commerce:merchant:query', 'commerce:merchant:audit',
                     'commerce:store:self-query', 'commerce:store:self-update') AND deleted = b'0'
GROUP BY permission;
SELECT COUNT(*) AS merchant_owner_self_permission_count
FROM system_role_menu rm JOIN system_role r ON r.id = rm.role_id JOIN system_menu m ON m.id = rm.menu_id
WHERE r.code = 'merchant_owner' AND r.tenant_id = 0 AND rm.deleted = b'0'
  AND m.permission IN ('commerce:store:self-query', 'commerce:store:self-update') AND m.deleted = b'0';

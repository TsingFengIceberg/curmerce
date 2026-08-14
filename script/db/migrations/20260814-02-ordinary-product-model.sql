-- Curmerce ordinary product persistence foundation, v1.
-- Prerequisite: 20260813-01-merchant-onboarding.sql.
-- Review every preflight result before continuing. Any existing planned object
-- or incompatible row is a stop condition; this file does not repair drift.
SET NAMES utf8mb4;

-- MySQL 8.0.16+ enforces CHECK constraints. This migration was verified for
-- the local MySQL 8.4 baseline and must not be silently weakened elsewhere.
SELECT VERSION() AS mysql_version, @@default_storage_engine AS default_engine,
       @@character_set_server AS server_charset, @@collation_server AS server_collation;
SELECT table_name, engine, table_collation
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('commerce_product_category', 'commerce_product', 'commerce_product_sku');
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('commerce_store', 'commerce_product_category', 'commerce_product', 'commerce_product_sku')
GROUP BY table_name, index_name;
SELECT id, merchant_id, COUNT(*) AS duplicate_count
FROM commerce_store
GROUP BY id, merchant_id
HAVING COUNT(*) > 1;
SELECT id, merchant_id
FROM commerce_store
WHERE id IS NULL OR merchant_id IS NULL;

-- Required because MySQL foreign keys reference a unique key whose first two
-- columns prove the store ID belongs to the product's merchant.
ALTER TABLE commerce_store
  ADD UNIQUE KEY uk_commerce_store_id_merchant (id, merchant_id);

CREATE TABLE commerce_product_category (
  id BIGINT NOT NULL AUTO_INCREMENT,
  parent_id BIGINT NULL,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(64) NOT NULL,
  image_url VARCHAR(1024) NULL,
  sort INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 0,
  creator VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_commerce_product_category_code (code),
  KEY idx_commerce_product_category_parent_status_sort (parent_id, status, sort),
  CONSTRAINT fk_commerce_product_category_parent
    FOREIGN KEY (parent_id) REFERENCES commerce_product_category (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_commerce_product_category_status CHECK (status IN (0, 1)),
  CONSTRAINT chk_commerce_product_category_sort CHECK (sort >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Curmerce platform product category';

CREATE TABLE commerce_product (
  id BIGINT NOT NULL AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  subtitle VARCHAR(255) NULL,
  main_image_url VARCHAR(1024) NOT NULL,
  image_urls JSON NULL,
  description LONGTEXT NOT NULL,
  audit_status TINYINT NOT NULL DEFAULT 0,
  sale_status TINYINT NOT NULL DEFAULT 0,
  reviewer_user_id BIGINT NULL,
  review_time DATETIME NULL,
  reject_reason VARCHAR(255) NULL,
  sort INT NOT NULL DEFAULT 0,
  creator VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_commerce_product_id_merchant (id, merchant_id),
  UNIQUE KEY uk_commerce_product_merchant_code (merchant_id, code),
  KEY idx_commerce_product_merchant_state_time (merchant_id, audit_status, sale_status, create_time),
  KEY idx_commerce_product_store_state_time (store_id, audit_status, sale_status, create_time),
  KEY idx_commerce_product_public_category (category_id, audit_status, sale_status, sort, id),
  CONSTRAINT fk_commerce_product_merchant
    FOREIGN KEY (merchant_id) REFERENCES commerce_merchant (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_commerce_product_store_owner
    FOREIGN KEY (store_id, merchant_id) REFERENCES commerce_store (id, merchant_id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_commerce_product_category
    FOREIGN KEY (category_id) REFERENCES commerce_product_category (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_commerce_product_audit_status CHECK (audit_status IN (0, 1, 2, 3)),
  CONSTRAINT chk_commerce_product_sale_status CHECK (sale_status IN (0, 1)),
  CONSTRAINT chk_commerce_product_sale_requires_approval CHECK (sale_status = 0 OR audit_status = 2),
  CONSTRAINT chk_commerce_product_sort CHECK (sort >= 0),
  CONSTRAINT chk_commerce_product_image_urls CHECK (
    image_urls IS NULL OR JSON_TYPE(image_urls) = 'ARRAY'
  ),
  CONSTRAINT chk_commerce_product_review_metadata CHECK (
    (audit_status IN (0, 1)
      AND reviewer_user_id IS NULL AND review_time IS NULL AND reject_reason IS NULL)
    OR (audit_status = 2
      AND reviewer_user_id IS NOT NULL AND review_time IS NOT NULL AND reject_reason IS NULL)
    OR (audit_status = 3
      AND reviewer_user_id IS NOT NULL AND review_time IS NOT NULL
      AND reject_reason IS NOT NULL AND CHAR_LENGTH(TRIM(reject_reason)) > 0)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Curmerce ordinary merchant product';

CREATE TABLE commerce_product_sku (
  id BIGINT NOT NULL AUTO_INCREMENT,
  product_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  code VARCHAR(64) NOT NULL,
  specification_values JSON NULL,
  image_url VARCHAR(1024) NULL,
  price BIGINT NOT NULL,
  market_price BIGINT NULL,
  stock INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 0,
  sort INT NOT NULL DEFAULT 0,
  creator VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_commerce_product_sku_merchant_code (merchant_id, code),
  KEY idx_commerce_product_sku_product_status_sort (product_id, status, sort, id),
  CONSTRAINT fk_commerce_product_sku_owner
    FOREIGN KEY (product_id, merchant_id) REFERENCES commerce_product (id, merchant_id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_commerce_product_sku_price CHECK (price >= 0),
  CONSTRAINT chk_commerce_product_sku_market_price CHECK (market_price IS NULL OR market_price >= 0),
  CONSTRAINT chk_commerce_product_sku_stock CHECK (stock >= 0),
  CONSTRAINT chk_commerce_product_sku_status CHECK (status IN (0, 1)),
  CONSTRAINT chk_commerce_product_sku_sort CHECK (sort >= 0),
  CONSTRAINT chk_commerce_product_sku_specification_values CHECK (
    specification_values IS NULL OR JSON_TYPE(specification_values) = 'ARRAY'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Curmerce ordinary product SKU';

-- Post-apply checks. Expected: one row per table, the named indexes and
-- constraints, native JSON columns, and no unexpected tenant_id columns.
SELECT table_name, engine, table_collation
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('commerce_product_category', 'commerce_product', 'commerce_product_sku')
ORDER BY table_name;
SELECT table_name, column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('commerce_product_category', 'commerce_product', 'commerce_product_sku')
  AND column_name IN ('image_urls', 'specification_values', 'tenant_id', 'price', 'stock')
ORDER BY table_name, ordinal_position;
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('commerce_store', 'commerce_product_category', 'commerce_product', 'commerce_product_sku')
GROUP BY table_name, index_name
ORDER BY table_name, index_name;
SELECT table_name, constraint_name, column_name, referenced_table_name, referenced_column_name
FROM information_schema.key_column_usage
WHERE table_schema = DATABASE()
  AND table_name IN ('commerce_product_category', 'commerce_product', 'commerce_product_sku')
  AND referenced_table_name IS NOT NULL
ORDER BY table_name, constraint_name, ordinal_position;
SELECT tc.constraint_name, tc.table_name, tc.enforced, cc.check_clause
FROM information_schema.table_constraints tc
JOIN information_schema.check_constraints cc
  ON cc.constraint_schema = tc.constraint_schema
 AND cc.constraint_name = tc.constraint_name
WHERE tc.constraint_schema = DATABASE()
  AND tc.constraint_type = 'CHECK'
  AND tc.constraint_name LIKE 'chk_commerce_product%'
ORDER BY tc.table_name, tc.constraint_name;

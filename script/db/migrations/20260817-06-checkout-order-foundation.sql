-- Curmerce buyer checkout and single-store pending-payment orders.
-- Review every preflight result before applying. MySQL 8.0.16+ is required.
SET NAMES utf8mb4;

SELECT VERSION() AS mysql_version;
SELECT table_name, engine, table_collation
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('commerce_order', 'commerce_order_item');
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('commerce_order', 'commerce_order_item')
GROUP BY table_name, index_name;

CREATE TABLE commerce_order (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_no VARCHAR(40) NOT NULL,
  member_user_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  idempotency_key VARCHAR(64) NOT NULL,
  status TINYINT NOT NULL,
  item_count INT NOT NULL,
  total_amount BIGINT NOT NULL,
  payable_amount BIGINT NOT NULL,
  receiver_name VARCHAR(30) NOT NULL,
  receiver_mobile VARCHAR(11) NOT NULL,
  receiver_area_id INT NOT NULL,
  receiver_area_name VARCHAR(64) NULL,
  receiver_detail_address VARCHAR(255) NOT NULL,
  creator VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_commerce_order_order_no (order_no),
  UNIQUE KEY uk_commerce_order_member_idempotency (member_user_id, idempotency_key),
  KEY idx_commerce_order_member_status_time (member_user_id, status, create_time, id),
  KEY idx_commerce_order_merchant_status_time (merchant_id, status, create_time, id),
  CONSTRAINT chk_commerce_order_status CHECK (status IN (10)),
  CONSTRAINT chk_commerce_order_item_count CHECK (item_count > 0),
  CONSTRAINT chk_commerce_order_total_amount CHECK (total_amount >= 0),
  CONSTRAINT chk_commerce_order_payable_amount CHECK (payable_amount >= 0),
  CONSTRAINT chk_commerce_order_receiver_name CHECK (CHAR_LENGTH(TRIM(receiver_name)) BETWEEN 1 AND 30),
  CONSTRAINT chk_commerce_order_receiver_mobile CHECK (CHAR_LENGTH(TRIM(receiver_mobile)) BETWEEN 7 AND 11),
  CONSTRAINT chk_commerce_order_receiver_address CHECK (CHAR_LENGTH(TRIM(receiver_detail_address)) BETWEEN 1 AND 255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Curmerce buyer order snapshot';

CREATE TABLE commerce_order_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  product_name VARCHAR(128) NOT NULL,
  product_image_url VARCHAR(1024) NOT NULL,
  sku_code VARCHAR(64) NOT NULL,
  specification_values JSON NULL,
  sku_image_url VARCHAR(1024) NULL,
  price BIGINT NOT NULL,
  quantity INT NOT NULL,
  total_amount BIGINT NOT NULL,
  creator VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_commerce_order_item_order_sku (order_id, sku_id),
  KEY idx_commerce_order_item_order_id (order_id, id),
  KEY idx_commerce_order_item_product_sku (product_id, sku_id),
  CONSTRAINT fk_commerce_order_item_order FOREIGN KEY (order_id) REFERENCES commerce_order (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_commerce_order_item_price CHECK (price >= 0),
  CONSTRAINT chk_commerce_order_item_quantity CHECK (quantity BETWEEN 1 AND 99),
  CONSTRAINT chk_commerce_order_item_total_amount CHECK (total_amount >= 0),
  CONSTRAINT chk_commerce_order_item_specification_values CHECK (
    specification_values IS NULL OR JSON_TYPE(specification_values) = 'ARRAY'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Curmerce immutable order item snapshot';

-- Deliberately no foreign keys to member, merchant, store, product, or SKU:
-- those are module ownership boundaries and may later be extracted.
SELECT table_name, column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('commerce_order', 'commerce_order_item')
ORDER BY table_name, ordinal_position;
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('commerce_order', 'commerce_order_item')
GROUP BY table_name, index_name
ORDER BY table_name, index_name;
SELECT table_name, constraint_name, column_name, referenced_table_name, referenced_column_name
FROM information_schema.key_column_usage
WHERE table_schema = DATABASE()
  AND table_name IN ('commerce_order', 'commerce_order_item')
  AND referenced_table_name IS NOT NULL
ORDER BY table_name, constraint_name, ordinal_position;

-- Curmerce simulated payment intent and idempotent success callback.
-- Review every preflight result before applying. MySQL 8.0.16+ is required.
SET NAMES utf8mb4;

SELECT VERSION() AS mysql_version;
SELECT status, COUNT(*) AS order_count
FROM commerce_order
GROUP BY status
ORDER BY status;
SELECT COUNT(*) AS existing_payment_table
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'commerce_payment';

ALTER TABLE commerce_order
  DROP CHECK chk_commerce_order_status,
  ADD CONSTRAINT chk_commerce_order_status CHECK (status IN (10, 20));

CREATE TABLE commerce_payment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  payment_no VARCHAR(40) NOT NULL,
  order_id BIGINT NOT NULL,
  order_no VARCHAR(40) NOT NULL,
  member_user_id BIGINT NOT NULL,
  amount BIGINT NOT NULL,
  status TINYINT NOT NULL,
  callback_id VARCHAR(64) NULL,
  paid_time DATETIME NULL,
  creator VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_commerce_payment_payment_no (payment_no),
  UNIQUE KEY uk_commerce_payment_order (order_id),
  KEY idx_commerce_payment_member_status_time (member_user_id, status, create_time, id),
  CONSTRAINT fk_commerce_payment_order FOREIGN KEY (order_id) REFERENCES commerce_order (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_commerce_payment_status CHECK (status IN (10, 20)),
  CONSTRAINT chk_commerce_payment_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Curmerce simulated payment intent and callback state';

SELECT table_name, column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'commerce_payment'
ORDER BY ordinal_position;
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'commerce_payment'
GROUP BY table_name, index_name
ORDER BY table_name, index_name;
SELECT table_name, constraint_name, column_name, referenced_table_name, referenced_column_name
FROM information_schema.key_column_usage
WHERE table_schema = DATABASE() AND table_name = 'commerce_payment'
  AND referenced_table_name IS NOT NULL
ORDER BY table_name, constraint_name, ordinal_position;

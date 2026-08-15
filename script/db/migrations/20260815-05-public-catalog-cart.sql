-- Curmerce public catalog indexes and buyer cart. Review preflight before applying.
SET NAMES utf8mb4;
SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'commerce_cart_item';
SELECT index_name FROM information_schema.statistics WHERE table_schema = DATABASE()
  AND table_name = 'commerce_product' AND index_name = 'idx_commerce_product_public_state_sort';

ALTER TABLE commerce_product ADD KEY idx_commerce_product_public_state_sort (audit_status, sale_status, sort, id);
ALTER TABLE commerce_product_sku ADD UNIQUE KEY uk_commerce_product_sku_id_product (id, product_id);

CREATE TABLE commerce_cart_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  member_user_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  selected TINYINT(1) NOT NULL DEFAULT 1,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id), UNIQUE KEY uk_commerce_cart_user_sku (member_user_id, sku_id),
  KEY idx_commerce_cart_user_id (member_user_id, id),
  CONSTRAINT fk_commerce_cart_sku_product FOREIGN KEY (sku_id, product_id)
    REFERENCES commerce_product_sku (id, product_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_commerce_cart_quantity CHECK (quantity BETWEEN 1 AND 99),
  CONSTRAINT chk_commerce_cart_selected CHECK (selected IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Curmerce buyer cart intent';

-- No tenant_id or member FK is intentional: member ownership is an API boundary
-- so the cart can later move to a commerce database without cross-module FK coupling.
SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = DATABASE()
  AND table_name = 'commerce_cart_item' ORDER BY ordinal_position;
SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
  FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'commerce_cart_item' GROUP BY index_name;

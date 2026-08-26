-- Curmerce product lifecycle operation history. Apply after 20260826-20-product-favorite.sql.
SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()
  AND table_name = 'commerce_product_operation_log';

CREATE TABLE commerce_product_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    operator_user_id BIGINT NULL,
    operator_type TINYINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    from_audit_status TINYINT NULL,
    to_audit_status TINYINT NULL,
    from_sale_status TINYINT NULL,
    to_sale_status TINYINT NULL,
    remark VARCHAR(255) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_commerce_product_operation_log_product_time (product_id, id),
    CONSTRAINT fk_commerce_product_operation_log_product FOREIGN KEY (product_id)
        REFERENCES commerce_product (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_commerce_product_operation_log_operator_type CHECK (operator_type IN (1, 2, 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Curmerce product lifecycle operation history';

SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = DATABASE()
  AND table_name = 'commerce_product_operation_log' ORDER BY ordinal_position;

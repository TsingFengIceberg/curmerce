-- Curmerce member product favorites. Apply after 20260815-05-public-catalog-cart.sql.
SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()
  AND table_name = 'commerce_product_favorite';

CREATE TABLE commerce_product_favorite (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    creator VARCHAR(64) NOT NULL DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) NOT NULL DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_commerce_product_favorite_user_product (member_user_id, product_id),
    KEY idx_commerce_product_favorite_product_time (product_id, create_time),
    CONSTRAINT fk_commerce_product_favorite_product FOREIGN KEY (product_id)
        REFERENCES commerce_product (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Curmerce member product favorites';

SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = DATABASE()
  AND table_name = 'commerce_product_favorite' ORDER BY ordinal_position;
SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
  FROM information_schema.statistics WHERE table_schema = DATABASE()
  AND table_name = 'commerce_product_favorite' GROUP BY index_name;

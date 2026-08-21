-- Curmerce personal one-item listings.
-- Existing product and order rows are ordinary merchant transactions and are
-- backfilled as seller_type = 1 before nullable ownership fields are enabled.
SET NAMES utf8mb4;

ALTER TABLE commerce_product
  ADD COLUMN seller_type TINYINT NULL AFTER store_id,
  ADD COLUMN seller_user_id BIGINT NULL AFTER seller_type,
  ADD COLUMN item_condition VARCHAR(32) NULL AFTER name;

UPDATE commerce_product
SET seller_type = 1
WHERE seller_type IS NULL;

ALTER TABLE commerce_product
  MODIFY merchant_id BIGINT NULL,
  MODIFY store_id BIGINT NULL,
  MODIFY seller_type TINYINT NOT NULL DEFAULT 1;

ALTER TABLE commerce_product
  ADD KEY idx_commerce_product_seller_state (seller_type, seller_user_id, audit_status, sale_status, id),
  ADD CONSTRAINT chk_commerce_product_seller_type CHECK (seller_type IN (1, 2)),
  ADD CONSTRAINT chk_commerce_product_seller_owner CHECK (
    (seller_type = 1 AND merchant_id IS NOT NULL AND store_id IS NOT NULL AND seller_user_id IS NULL)
    OR (seller_type = 2 AND merchant_id IS NULL AND store_id IS NULL AND seller_user_id IS NOT NULL)
  );

ALTER TABLE commerce_product_sku
  MODIFY merchant_id BIGINT NULL;

ALTER TABLE commerce_order
  ADD COLUMN seller_type TINYINT NULL AFTER store_id,
  ADD COLUMN seller_user_id BIGINT NULL AFTER seller_type;

UPDATE commerce_order
SET seller_type = 1
WHERE seller_type IS NULL;

ALTER TABLE commerce_order
  MODIFY merchant_id BIGINT NULL,
  MODIFY store_id BIGINT NULL,
  MODIFY seller_type TINYINT NOT NULL DEFAULT 1;

ALTER TABLE commerce_order
  ADD KEY idx_commerce_order_seller_state (seller_type, seller_user_id, status, create_time, id),
  ADD CONSTRAINT chk_commerce_order_seller_type CHECK (seller_type IN (1, 2)),
  ADD CONSTRAINT chk_commerce_order_seller_owner CHECK (
    (seller_type = 1 AND merchant_id IS NOT NULL AND store_id IS NOT NULL AND seller_user_id IS NULL)
    OR (seller_type = 2 AND merchant_id IS NULL AND store_id IS NULL AND seller_user_id IS NOT NULL)
  );

SELECT table_name, column_name, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('commerce_product', 'commerce_product_sku', 'commerce_order')
  AND column_name IN ('merchant_id', 'store_id', 'seller_type', 'seller_user_id', 'item_condition')
ORDER BY table_name, ordinal_position;

SELECT table_name, constraint_name
FROM information_schema.table_constraints
WHERE table_schema = DATABASE()
  AND constraint_name IN ('chk_commerce_product_seller_type', 'chk_commerce_product_seller_owner',
                          'chk_commerce_order_seller_type', 'chk_commerce_order_seller_owner')
ORDER BY table_name, constraint_name;

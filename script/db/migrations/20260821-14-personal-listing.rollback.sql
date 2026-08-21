-- Rollback review aid for 20260821-14-personal-listing.sql.
-- Personal rows must be removed or migrated back to merchant ownership before
-- restoring NOT NULL merchant/store columns.
SET NAMES utf8mb4;

SELECT COUNT(*) AS personal_product_rows
FROM commerce_product
WHERE seller_type = 2 OR seller_user_id IS NOT NULL;
SELECT COUNT(*) AS personal_order_rows
FROM commerce_order
WHERE seller_type = 2 OR seller_user_id IS NOT NULL;

ALTER TABLE commerce_product
  DROP CHECK chk_commerce_product_seller_owner,
  DROP CHECK chk_commerce_product_seller_type,
  DROP KEY idx_commerce_product_seller_state,
  DROP COLUMN item_condition,
  DROP COLUMN seller_user_id,
  DROP COLUMN seller_type,
  MODIFY merchant_id BIGINT NOT NULL,
  MODIFY store_id BIGINT NOT NULL;

ALTER TABLE commerce_product_sku
  MODIFY merchant_id BIGINT NOT NULL;

ALTER TABLE commerce_order
  DROP CHECK chk_commerce_order_seller_owner,
  DROP CHECK chk_commerce_order_seller_type,
  DROP KEY idx_commerce_order_seller_state,
  DROP COLUMN seller_user_id,
  DROP COLUMN seller_type,
  MODIFY merchant_id BIGINT NOT NULL,
  MODIFY store_id BIGINT NOT NULL;

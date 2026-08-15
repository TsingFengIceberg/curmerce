-- Guarded review aid only; inspect row counts and external references first.
SELECT table_name, table_rows FROM information_schema.tables WHERE table_schema = DATABASE()
  AND table_name = 'commerce_cart_item';
-- On a disposable database after explicit approval:
-- DROP TABLE commerce_cart_item;
-- ALTER TABLE commerce_product_sku DROP INDEX uk_commerce_product_sku_id_product;
-- ALTER TABLE commerce_product DROP INDEX idx_commerce_product_public_state_sort;

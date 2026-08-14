-- Disposable local review aid only. Never run automatically and never use it
-- to remove retained product, category, merchant, or store data.
-- MySQL DDL auto-commits; inspect information_schema after every partial run.

-- Stop unless every count below is zero and no external table references exist.
SELECT 'commerce_product_sku' AS table_name, COUNT(*) AS retained_rows
FROM commerce_product_sku;
SELECT 'commerce_product' AS table_name, COUNT(*) AS retained_rows
FROM commerce_product;
SELECT 'commerce_product_category' AS table_name, COUNT(*) AS retained_rows
FROM commerce_product_category;
SELECT COUNT(*) AS external_product_fk_count
FROM information_schema.key_column_usage
WHERE constraint_schema = DATABASE()
  AND referenced_table_name IN ('commerce_product_category', 'commerce_product', 'commerce_product_sku')
  AND table_name NOT IN ('commerce_product_category', 'commerce_product', 'commerce_product_sku');

-- Only after an explicit backup, zero-row proof, and user-approved disposable
-- local rollback, execute manually in child-to-parent order:
-- DROP TABLE commerce_product_sku;
-- DROP TABLE commerce_product;
-- DROP TABLE commerce_product_category;
-- ALTER TABLE commerce_store DROP INDEX uk_commerce_store_id_merchant;

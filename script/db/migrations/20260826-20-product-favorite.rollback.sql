-- Review favorite row counts before using this rollback on a disposable database.
SELECT table_name, table_rows FROM information_schema.tables WHERE table_schema = DATABASE()
  AND table_name = 'commerce_product_favorite';
-- DROP TABLE commerce_product_favorite;

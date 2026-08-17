-- Guarded review aid only; inspect row counts and external references first.
SELECT table_name, table_rows
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('commerce_order', 'commerce_order_item');

SELECT COUNT(*) AS order_count FROM commerce_order;
SELECT COUNT(*) AS order_item_count FROM commerce_order_item;

-- On a disposable database after explicit approval, and only after dependent
-- application code has been removed:
-- DROP TABLE commerce_order_item;
-- DROP TABLE commerce_order;

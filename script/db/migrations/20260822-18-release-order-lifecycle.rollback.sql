SET @curmerce_release_order_index_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'commerce_release_purchase' AND index_name = 'idx_release_purchase_order'
);
SET @curmerce_release_order_index_sql = IF(@curmerce_release_order_index_exists = 1,
  'ALTER TABLE commerce_release_purchase DROP INDEX idx_release_purchase_order',
  'SELECT 1');
PREPARE curmerce_release_order_index_stmt FROM @curmerce_release_order_index_sql;
EXECUTE curmerce_release_order_index_stmt;
DEALLOCATE PREPARE curmerce_release_order_index_stmt;

SET @curmerce_release_order_column_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'commerce_release_purchase' AND column_name = 'order_id'
);
SET @curmerce_release_order_column_sql = IF(@curmerce_release_order_column_exists = 1,
  'ALTER TABLE commerce_release_purchase DROP COLUMN order_id',
  'SELECT 1');
PREPARE curmerce_release_order_column_stmt FROM @curmerce_release_order_column_sql;
EXECUTE curmerce_release_order_column_stmt;
DEALLOCATE PREPARE curmerce_release_order_column_stmt;

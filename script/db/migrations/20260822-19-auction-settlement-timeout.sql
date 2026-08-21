-- Mark a basic auction as failed when its winning order expires unpaid.
-- The existing order timeout transaction still restores SKU stock locally.
SET NAMES utf8mb4;

SET @curmerce_auction_status_constraint_exists = (
  SELECT COUNT(*) FROM information_schema.table_constraints
  WHERE table_schema = DATABASE() AND table_name = 'commerce_auction_session'
    AND constraint_name = 'chk_auction_status'
);
SET @curmerce_auction_status_constraint_sql = IF(@curmerce_auction_status_constraint_exists > 0,
  'ALTER TABLE commerce_auction_session DROP CONSTRAINT chk_auction_status', 'SELECT 1');
PREPARE curmerce_auction_status_constraint_stmt FROM @curmerce_auction_status_constraint_sql;
EXECUTE curmerce_auction_status_constraint_stmt;
DEALLOCATE PREPARE curmerce_auction_status_constraint_stmt;

SET @curmerce_auction_failed_time_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'commerce_auction_session'
    AND column_name = 'settlement_failed_time'
);
SET @curmerce_auction_failed_time_sql = IF(@curmerce_auction_failed_time_exists = 0,
  'ALTER TABLE commerce_auction_session ADD COLUMN settlement_failed_time DATETIME NULL AFTER settlement_order_id',
  'SELECT 1');
PREPARE curmerce_auction_failed_time_stmt FROM @curmerce_auction_failed_time_sql;
EXECUTE curmerce_auction_failed_time_stmt;
DEALLOCATE PREPARE curmerce_auction_failed_time_stmt;

SET @curmerce_auction_reason_exists = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'commerce_auction_session'
    AND column_name = 'settlement_failure_reason'
);
SET @curmerce_auction_reason_sql = IF(@curmerce_auction_reason_exists = 0,
  'ALTER TABLE commerce_auction_session ADD COLUMN settlement_failure_reason VARCHAR(255) NULL AFTER settlement_failed_time',
  'SELECT 1');
PREPARE curmerce_auction_reason_stmt FROM @curmerce_auction_reason_sql;
EXECUTE curmerce_auction_reason_stmt;
DEALLOCATE PREPARE curmerce_auction_reason_stmt;

ALTER TABLE commerce_auction_session
  ADD CONSTRAINT chk_auction_status CHECK (status IN (0, 10, 20, 30, 40, 50));

SET @curmerce_auction_index_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'commerce_auction_session'
    AND index_name = 'idx_auction_settlement_order'
);
SET @curmerce_auction_index_sql = IF(@curmerce_auction_index_exists = 0,
  'ALTER TABLE commerce_auction_session ADD KEY idx_auction_settlement_order (settlement_order_id, status, id)',
  'SELECT 1');
PREPARE curmerce_auction_index_stmt FROM @curmerce_auction_index_sql;
EXECUTE curmerce_auction_index_stmt;
DEALLOCATE PREPARE curmerce_auction_index_stmt;

SELECT column_name FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'commerce_auction_session'
  AND column_name IN ('settlement_failed_time', 'settlement_failure_reason');

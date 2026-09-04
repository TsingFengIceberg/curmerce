-- Bound the retry loop for Redis finalization after a committed SQL purchase.
-- A DEAD row is operator-visible and can be replayed after Redis or the
-- deployment has been repaired. This migration is additive and idempotent.
SET NAMES utf8mb4;
SET @db = DATABASE();

SET @table_exists = (SELECT COUNT(*) FROM information_schema.tables
                     WHERE table_schema=@db AND table_name='commerce_release_reservation');
SET @sql = IF(@table_exists = 1 AND
              (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db
               AND table_name='commerce_release_reservation' AND column_name='attempts') = 0,
  'ALTER TABLE commerce_release_reservation ADD COLUMN attempts INT NOT NULL DEFAULT 0 AFTER status', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF(@table_exists = 1 AND
              (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db
               AND table_name='commerce_release_reservation' AND column_name='next_retry_at') = 0,
  'ALTER TABLE commerce_release_reservation ADD COLUMN next_retry_at DATETIME NULL AFTER attempts', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF(@table_exists = 1 AND
              (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=@db
               AND table_name='commerce_release_reservation' AND index_name='idx_release_reservation_retry') = 0,
  'ALTER TABLE commerce_release_reservation ADD KEY idx_release_reservation_retry (tenant_id, status, next_retry_at, id)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @constraint_exists = (SELECT COUNT(*) FROM information_schema.table_constraints
                          WHERE constraint_schema=@db AND table_name='commerce_release_reservation'
                            AND constraint_name='chk_release_reservation_status');
SET @sql = IF(@table_exists = 1 AND @constraint_exists = 1,
  'ALTER TABLE commerce_release_reservation DROP CHECK chk_release_reservation_status', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF(@table_exists = 1,
  'ALTER TABLE commerce_release_reservation ADD CONSTRAINT chk_release_reservation_status CHECK (status IN (20, 30, 40))', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SELECT table_name, column_name, data_type, column_default
FROM information_schema.columns
WHERE table_schema=@db AND table_name='commerce_release_reservation'
  AND column_name IN ('attempts','next_retry_at');
SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics
WHERE table_schema=@db AND table_name='commerce_release_reservation'
  AND index_name='idx_release_reservation_retry'
GROUP BY index_name;

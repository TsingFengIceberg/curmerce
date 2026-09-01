-- Reliable Kafka processing ledger: payload retention, status, retry count,
-- and operator replay. Apply in the Core (curmerce) schema after migration 25.
SET NAMES utf8mb4;

SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
          AND table_name = 'commerce_kafka_consumer_receipt' AND column_name = 'payload');
SET @sql = IF(@c = 0, 'ALTER TABLE commerce_kafka_consumer_receipt ADD COLUMN payload JSON NULL AFTER event_key', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
          AND table_name = 'commerce_kafka_consumer_receipt' AND column_name = 'status');
SET @sql = IF(@c = 0, 'ALTER TABLE commerce_kafka_consumer_receipt ADD COLUMN status TINYINT NOT NULL DEFAULT 10 AFTER payload', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
          AND table_name = 'commerce_kafka_consumer_receipt' AND column_name = 'attempts');
SET @sql = IF(@c = 0, 'ALTER TABLE commerce_kafka_consumer_receipt ADD COLUMN attempts INT NOT NULL DEFAULT 0 AFTER status', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
          AND table_name = 'commerce_kafka_consumer_receipt' AND column_name = 'processing_time');
SET @sql = IF(@c = 0, 'ALTER TABLE commerce_kafka_consumer_receipt ADD COLUMN processing_time DATETIME NULL AFTER attempts', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
          AND table_name = 'commerce_kafka_consumer_receipt' AND column_name = 'last_error');
SET @sql = IF(@c = 0, 'ALTER TABLE commerce_kafka_consumer_receipt ADD COLUMN last_error VARCHAR(500) NULL AFTER attempts', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
          AND table_name = 'commerce_kafka_consumer_receipt' AND column_name = 'processed_time');
SET @sql = IF(@c = 0, 'ALTER TABLE commerce_kafka_consumer_receipt ADD COLUMN processed_time DATETIME NULL AFTER received_time', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @c = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
          AND table_name = 'commerce_kafka_consumer_receipt' AND index_name = 'idx_commerce_kafka_receipt_status');
SET @sql = IF(@c = 0, 'ALTER TABLE commerce_kafka_consumer_receipt ADD KEY idx_commerce_kafka_receipt_status (status, processing_time, attempts, id)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

UPDATE commerce_kafka_consumer_receipt SET status = 20, attempts = GREATEST(attempts, 1), processed_time = received_time
WHERE status = 10 AND processed_time IS NULL;

SELECT column_name, data_type, is_nullable FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'commerce_kafka_consumer_receipt'
ORDER BY ordinal_position;

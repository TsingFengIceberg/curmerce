-- Durable duplicate guard for the optional Kafka commerce event consumer.
-- Apply in the Core (curmerce) schema before enabling CURMERCE_OUTBOX_TRANSPORT=kafka.
SET NAMES utf8mb4;

CREATE TABLE commerce_kafka_consumer_receipt (
  id BIGINT NOT NULL AUTO_INCREMENT,
  consumer_group VARCHAR(128) NOT NULL,
  event_id BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  event_key VARCHAR(128) NOT NULL,
  received_time DATETIME NOT NULL,
  creator VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_commerce_kafka_receipt_group_event (consumer_group, event_id),
  KEY idx_commerce_kafka_receipt_type_time (event_type, received_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Idempotent receipt ledger for optional Kafka commerce consumers';

SELECT table_name, column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'commerce_kafka_consumer_receipt'
ORDER BY ordinal_position;

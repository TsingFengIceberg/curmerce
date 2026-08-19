-- Curmerce transactional Outbox and reconciliation ledger foundation, v1.
-- The outbox stores domain events appended inside the same local transaction as
-- the business change; a publisher job delivers them to Redis Stream and marks
-- them published, retried, or dead. The reconciliation ledger records detected
-- order/payment/refund consistency issues for later manual or automated repair.
-- MySQL 8.0.16+ is required. Review the preflight results before applying.
SET NAMES utf8mb4;

SELECT VERSION() AS mysql_version, @@default_storage_engine AS default_engine,
       @@character_set_server AS server_charset, @@collation_server AS server_collation;
SELECT table_name, engine, table_collation
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('commerce_outbox_event', 'commerce_reconciliation_issue');

CREATE TABLE commerce_outbox_event (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_type VARCHAR(64) NOT NULL,
  event_key VARCHAR(128) NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id BIGINT NOT NULL,
  payload TEXT NOT NULL,
  status TINYINT NOT NULL DEFAULT 10,
  attempts INT NOT NULL DEFAULT 0,
  next_retry_time DATETIME NULL,
  last_error VARCHAR(500) NULL,
  published_time DATETIME NULL,
  creator VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_commerce_outbox_event_type_key (event_type, event_key),
  KEY idx_commerce_outbox_event_publish (status, next_retry_time, id),
  CONSTRAINT chk_commerce_outbox_event_status CHECK (status IN (10, 20, 30)),
  CONSTRAINT chk_commerce_outbox_event_attempts CHECK (attempts >= 0),
  CONSTRAINT chk_commerce_outbox_event_payload CHECK (JSON_VALID(payload))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Curmerce transactional outbox events';

CREATE TABLE commerce_reconciliation_issue (
  id BIGINT NOT NULL AUTO_INCREMENT,
  issue_type VARCHAR(64) NOT NULL,
  order_id BIGINT NULL,
  payment_id BIGINT NULL,
  refund_id BIGINT NULL,
  description VARCHAR(1000) NOT NULL,
  status TINYINT NOT NULL DEFAULT 10,
  creator VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  KEY idx_commerce_reconciliation_issue_status (status, id),
  KEY idx_commerce_reconciliation_issue_scope (issue_type, order_id, payment_id, refund_id, status),
  CONSTRAINT chk_commerce_reconciliation_issue_status CHECK (status IN (10, 20))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Curmerce order/payment/refund reconciliation ledger';

SELECT table_name, column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('commerce_outbox_event', 'commerce_reconciliation_issue')
ORDER BY table_name, ordinal_position;
SELECT constraint_name, check_clause
FROM information_schema.check_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name IN ('chk_commerce_outbox_event_status', 'chk_commerce_outbox_event_attempts',
                          'chk_commerce_outbox_event_payload',
                          'chk_commerce_reconciliation_issue_status')
ORDER BY constraint_name;
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('commerce_outbox_event', 'commerce_reconciliation_issue')
GROUP BY table_name, index_name
ORDER BY table_name, index_name;

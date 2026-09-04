-- Durable Kafka command state for optional high-concurrency limited releases.
-- The table is written in the same local transaction as commerce_outbox_event.
-- Do not enable CURMERCE_RELEASE_KAFKA_QUEUE_ENABLED until this migration and
-- the explicit two-instance / compensation drill both pass.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS commerce_release_purchase_command (
  id BIGINT NOT NULL AUTO_INCREMENT,
  ticket CHAR(36) NOT NULL,
  buyer_user_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  address_id BIGINT NOT NULL,
  idempotency_key VARCHAR(64) NOT NULL,
  status TINYINT NOT NULL DEFAULT 10,
  attempts INT NOT NULL DEFAULT 0,
  dispatch_version INT NOT NULL DEFAULT 1,
  processing_token CHAR(36) NULL,
  processing_deadline DATETIME NULL,
  retry_at DATETIME NULL,
  result JSON NULL,
  last_error VARCHAR(500) NULL,
  creator VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_release_command_ticket (ticket, deleted),
  UNIQUE KEY uk_release_command_buyer_item_key (buyer_user_id, item_id, idempotency_key, deleted),
  KEY idx_release_command_recovery (status, retry_at, processing_deadline, id),
  CONSTRAINT chk_release_command_status CHECK (status IN (10, 20, 30, 40, 50)),
  CONSTRAINT chk_release_command_quantity CHECK (quantity > 0),
  CONSTRAINT chk_release_command_attempts CHECK (attempts >= 0),
  CONSTRAINT chk_release_command_dispatch_version CHECK (dispatch_version > 0),
  CONSTRAINT fk_release_command_item FOREIGN KEY (item_id) REFERENCES commerce_release_item(id)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Durable limited release Kafka command';

SELECT table_name, index_name FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'commerce_release_purchase_command'
ORDER BY index_name;

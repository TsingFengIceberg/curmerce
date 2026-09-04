-- Durable fence for Redis/Lua limited-release reservations.
-- A COMMITTED row is written with the purchase transaction.  The Redis
-- reservation may therefore be finalized after a process crash without
-- trusting an in-memory transaction callback.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS commerce_release_reservation (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
  reservation_key VARCHAR(128) NOT NULL,
  campaign_id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  buyer_user_id BIGINT NOT NULL,
  purchase_id BIGINT NULL,
  quantity INT NOT NULL,
  status TINYINT NOT NULL DEFAULT 20 COMMENT '20 committed, 30 Redis finalized',
  last_error VARCHAR(500) NULL,
  creator VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_release_reservation_identity (tenant_id, campaign_id, item_id, buyer_user_id, reservation_key, deleted),
  KEY idx_release_reservation_pending (status, id),
  KEY idx_release_reservation_purchase (purchase_id, status),
  CONSTRAINT chk_release_reservation_quantity CHECK (quantity > 0),
  CONSTRAINT chk_release_reservation_status CHECK (status IN (20, 30)),
  CONSTRAINT fk_release_reservation_item FOREIGN KEY (item_id) REFERENCES commerce_release_item(id)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Durable limited release Redis reservation fence';

SELECT table_name, index_name FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'commerce_release_reservation'
ORDER BY index_name;

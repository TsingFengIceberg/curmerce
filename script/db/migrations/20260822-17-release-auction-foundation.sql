-- Curmerce basic limited-release and auction foundation.
-- This version intentionally uses local MySQL transactions only. Redis/Lua,
-- queues, and distributed settlement are separate learning stages.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS commerce_release_campaign (
  id BIGINT NOT NULL AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL, store_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL, status TINYINT NOT NULL DEFAULT 0,
  start_time DATETIME NOT NULL, end_time DATETIME NOT NULL,
  per_user_limit INT NOT NULL,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), KEY idx_release_campaign_public (status, start_time, end_time, id),
  KEY idx_release_campaign_owner (merchant_id, store_id, status, id),
  CONSTRAINT chk_release_campaign_status CHECK (status IN (0, 10, 20, 30, 40)),
  CONSTRAINT chk_release_campaign_time CHECK (end_time > start_time),
  CONSTRAINT chk_release_campaign_limit CHECK (per_user_limit > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Curmerce limited release campaign';

CREATE TABLE IF NOT EXISTS commerce_release_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  campaign_id BIGINT NOT NULL, product_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
  campaign_price BIGINT NOT NULL, stock INT NOT NULL, sold_count INT NOT NULL DEFAULT 0,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), UNIQUE KEY uk_release_item_campaign_sku (campaign_id, sku_id, deleted),
  KEY idx_release_item_sku (sku_id, campaign_id, deleted),
  CONSTRAINT fk_release_item_campaign FOREIGN KEY (campaign_id) REFERENCES commerce_release_campaign(id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_release_item_price CHECK (campaign_price >= 0),
  CONSTRAINT chk_release_item_stock CHECK (stock >= 0),
  CONSTRAINT chk_release_item_sold CHECK (sold_count >= 0 AND sold_count <= stock + sold_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Limited release SKU inventory';

CREATE TABLE IF NOT EXISTS commerce_release_purchase (
  id BIGINT NOT NULL AUTO_INCREMENT,
  campaign_id BIGINT NOT NULL, item_id BIGINT NOT NULL, buyer_user_id BIGINT NOT NULL, order_id BIGINT NULL,
  quantity INT NOT NULL, unit_price BIGINT NOT NULL, status TINYINT NOT NULL DEFAULT 10,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), UNIQUE KEY uk_release_purchase_buyer_item (buyer_user_id, item_id, deleted),
  KEY idx_release_purchase_order (order_id, status, deleted),
  KEY idx_release_purchase_campaign (campaign_id, buyer_user_id, id),
  CONSTRAINT fk_release_purchase_item FOREIGN KEY (item_id) REFERENCES commerce_release_item(id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_release_purchase_quantity CHECK (quantity > 0),
  CONSTRAINT chk_release_purchase_price CHECK (unit_price >= 0),
  CONSTRAINT chk_release_purchase_status CHECK (status IN (10, 20, 30))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Limited release basic purchase record';

CREATE TABLE IF NOT EXISTS commerce_auction_session (
  id BIGINT NOT NULL AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL, store_id BIGINT NOT NULL, product_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL, status TINYINT NOT NULL DEFAULT 0,
  starting_price BIGINT NOT NULL, min_increment BIGINT NOT NULL,
  start_time DATETIME NOT NULL, end_time DATETIME NOT NULL,
  winner_user_id BIGINT NULL, winning_bid_id BIGINT NULL, settlement_order_id BIGINT NULL,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), KEY idx_auction_public (status, start_time, end_time, id),
  KEY idx_auction_owner (merchant_id, store_id, status, id),
  CONSTRAINT chk_auction_status CHECK (status IN (0, 10, 20, 30, 40)),
  CONSTRAINT chk_auction_time CHECK (end_time > start_time),
  CONSTRAINT chk_auction_start_price CHECK (starting_price >= 0),
  CONSTRAINT chk_auction_increment CHECK (min_increment > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Curmerce basic auction session';

CREATE TABLE IF NOT EXISTS commerce_auction_bid (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL, bidder_user_id BIGINT NOT NULL, amount BIGINT NOT NULL,
  idempotency_key VARCHAR(64) NOT NULL,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), UNIQUE KEY uk_auction_bid_session_key (session_id, idempotency_key, deleted),
  KEY idx_auction_bid_highest (session_id, amount DESC, id),
  KEY idx_auction_bid_user (bidder_user_id, session_id, id),
  CONSTRAINT fk_auction_bid_session FOREIGN KEY (session_id) REFERENCES commerce_auction_session(id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_auction_bid_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Curmerce auction bid';

INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, status, visible, keep_alive, always_show, creator, updater, deleted)
SELECT v.name, v.permission, 3, v.sort, 0, '', '#', '', 0, b'1', b'1', b'1', 'migration', 'migration', b'0'
FROM (
  SELECT 'Release create' name, 'commerce:release:create' permission, 60 sort
  UNION ALL SELECT 'Release query', 'commerce:release:query', 61
  UNION ALL SELECT 'Release update', 'commerce:release:update', 62
  UNION ALL SELECT 'Auction create', 'commerce:auction:create', 70
  UNION ALL SELECT 'Auction query', 'commerce:auction:query', 71
  UNION ALL SELECT 'Auction update', 'commerce:auction:update', 72
) v WHERE NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission = v.permission AND m.deleted = b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT r.id, m.id, 'migration', 'migration', b'0', 0
FROM system_role r JOIN system_menu m ON m.permission IN ('commerce:release:create','commerce:release:query','commerce:release:update','commerce:auction:create','commerce:auction:query','commerce:auction:update')
WHERE r.code = 'merchant_owner' AND r.tenant_id = 0 AND r.deleted = b'0' AND m.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = b'0');

SELECT table_name, index_name FROM information_schema.statistics WHERE table_schema = DATABASE()
  AND table_name IN ('commerce_release_campaign','commerce_release_item','commerce_release_purchase','commerce_auction_session','commerce_auction_bid')
ORDER BY table_name, index_name;

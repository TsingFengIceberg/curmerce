-- Auction ownership cutover: copy the current write model into a dedicated schema.
-- Run with the Core account after stopping Core/Auction writers. The old tables
-- are retained as a read-only rollback source until post-cutover verification.
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS curmerce_auction
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS curmerce_auction.auction_session (
  id BIGINT NOT NULL AUTO_INCREMENT,
  merchant_id BIGINT NOT NULL, store_id BIGINT NOT NULL, product_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
  name VARCHAR(120) NOT NULL, status TINYINT NOT NULL DEFAULT 0,
  starting_price BIGINT NOT NULL, min_increment BIGINT NOT NULL,
  start_time DATETIME NOT NULL, end_time DATETIME NOT NULL,
  winner_user_id BIGINT NULL, winning_bid_id BIGINT NULL, settlement_order_id BIGINT NULL,
  settlement_failed_time DATETIME NULL, settlement_failure_reason VARCHAR(255) NULL,
  product_name VARCHAR(255) NULL, product_image_url VARCHAR(1024) NULL, sku_label VARCHAR(255) NULL, original_price BIGINT NULL,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), KEY idx_auction_public (status, start_time, end_time, id), KEY idx_auction_owner (merchant_id, store_id, status, id),
  KEY idx_auction_settlement (settlement_order_id, status, id),
  CONSTRAINT chk_auction_owned_status CHECK (status IN (0, 10, 20, 30, 40, 50)),
  CONSTRAINT chk_auction_owned_time CHECK (end_time > start_time),
  CONSTRAINT chk_auction_owned_price CHECK (starting_price >= 0 AND min_increment > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Curmerce Auction-owned sessions';

CREATE TABLE IF NOT EXISTS curmerce_auction.auction_bid (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL, bidder_user_id BIGINT NOT NULL, amount BIGINT NOT NULL,
  idempotency_key VARCHAR(64) NOT NULL,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), UNIQUE KEY uk_auction_owned_bid_key (session_id, idempotency_key, deleted),
  KEY idx_auction_owned_bid_highest (session_id, amount DESC, id), KEY idx_auction_owned_bid_user (bidder_user_id, session_id, id),
  CONSTRAINT chk_auction_owned_bid_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Curmerce Auction-owned bids';

INSERT IGNORE INTO curmerce_auction.auction_session
  (id, merchant_id, store_id, product_id, sku_id, name, status, starting_price, min_increment, start_time, end_time,
   winner_user_id, winning_bid_id, settlement_order_id, settlement_failed_time, settlement_failure_reason,
   product_name, product_image_url, sku_label, original_price,
   creator, create_time, updater, update_time, deleted)
SELECT id, merchant_id, store_id, product_id, sku_id, name, status, starting_price, min_increment, start_time, end_time,
       winner_user_id, winning_bid_id, settlement_order_id, settlement_failed_time, settlement_failure_reason,
       p.name, COALESCE(s.image_url, p.main_image_url), s.code, s.price,
       creator, create_time, updater, update_time, deleted
FROM curmerce.commerce_auction_session a
LEFT JOIN curmerce.commerce_product p ON p.id = a.product_id
LEFT JOIN curmerce.commerce_product_sku s ON s.id = a.sku_id;

INSERT IGNORE INTO curmerce_auction.auction_bid
  (id, session_id, bidder_user_id, amount, idempotency_key, creator, create_time, updater, update_time, deleted)
SELECT id, session_id, bidder_user_id, amount, idempotency_key, creator, create_time, updater, update_time, deleted
FROM curmerce.commerce_auction_bid;

CREATE TABLE IF NOT EXISTS curmerce_auction.ownership_cutover (
  id BIGINT NOT NULL AUTO_INCREMENT,
  source_session_count BIGINT NOT NULL, target_session_count BIGINT NOT NULL,
  source_bid_count BIGINT NOT NULL, target_bid_count BIGINT NOT NULL,
  verified BIT(1) NOT NULL DEFAULT b'0', verified_time DATETIME NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO curmerce_auction.ownership_cutover
  (source_session_count, target_session_count, source_bid_count, target_bid_count, verified, verified_time)
SELECT (SELECT COUNT(*) FROM curmerce.commerce_auction_session),
       (SELECT COUNT(*) FROM curmerce_auction.auction_session),
       (SELECT COUNT(*) FROM curmerce.commerce_auction_bid),
       (SELECT COUNT(*) FROM curmerce_auction.auction_bid),
       IF((SELECT COUNT(*) FROM curmerce.commerce_auction_session) = (SELECT COUNT(*) FROM curmerce_auction.auction_session)
          AND (SELECT COUNT(*) FROM curmerce.commerce_auction_bid) = (SELECT COUNT(*) FROM curmerce_auction.auction_bid), b'1', b'0'),
       IF((SELECT COUNT(*) FROM curmerce.commerce_auction_session) = (SELECT COUNT(*) FROM curmerce_auction.auction_session)
          AND (SELECT COUNT(*) FROM curmerce.commerce_auction_bid) = (SELECT COUNT(*) FROM curmerce_auction.auction_bid), CURRENT_TIMESTAMP, NULL);

SELECT * FROM curmerce_auction.ownership_cutover ORDER BY id DESC LIMIT 1;

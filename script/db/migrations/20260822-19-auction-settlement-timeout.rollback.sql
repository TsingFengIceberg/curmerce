-- Roll back only the auction settlement-timeout metadata after clearing status 50 rows.
SET NAMES utf8mb4;
UPDATE commerce_auction_session SET status = 30, settlement_failed_time = NULL,
  settlement_failure_reason = NULL WHERE status = 50;
ALTER TABLE commerce_auction_session DROP CHECK chk_auction_status;
ALTER TABLE commerce_auction_session ADD CONSTRAINT chk_auction_status CHECK (status IN (0, 10, 20, 30, 40));
ALTER TABLE commerce_auction_session DROP INDEX idx_auction_settlement_order;
ALTER TABLE commerce_auction_session DROP COLUMN settlement_failure_reason;
ALTER TABLE commerce_auction_session DROP COLUMN settlement_failed_time;

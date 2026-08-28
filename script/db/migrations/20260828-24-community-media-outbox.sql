-- Community-owned desired-state outbox for reliable Core media-reference sync.
-- Apply after migration 23 while connected to curmerce_community.
SET NAMES utf8mb4;

CREATE TABLE community_media_outbox (
  id BIGINT NOT NULL AUTO_INCREMENT,
  business_type VARCHAR(64) NOT NULL,
  business_id VARCHAR(128) NOT NULL,
  field_name VARCHAR(64) NOT NULL,
  payload TEXT NOT NULL,
  version BIGINT NOT NULL DEFAULT 1,
  status TINYINT NOT NULL DEFAULT 10,
  attempts INT NOT NULL DEFAULT 0,
  next_retry_time DATETIME NULL,
  processing_token VARCHAR(64) NULL,
  lease_until DATETIME NULL,
  last_error VARCHAR(500) NULL,
  processed_time DATETIME NULL,
  creator VARCHAR(64) DEFAULT '',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_community_media_outbox_business
    (business_type, business_id, field_name),
  KEY idx_community_media_outbox_claim (status, next_retry_time, lease_until, id),
  CONSTRAINT chk_community_media_outbox_payload CHECK (JSON_VALID(payload)),
  CONSTRAINT chk_community_media_outbox_version CHECK (version > 0),
  CONSTRAINT chk_community_media_outbox_attempts CHECK (attempts >= 0),
  CONSTRAINT chk_community_media_outbox_status CHECK (status IN (10, 20, 30))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Latest desired Community media-reference state for reliable Core sync';

SELECT table_name, column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'community_media_outbox'
ORDER BY ordinal_position;

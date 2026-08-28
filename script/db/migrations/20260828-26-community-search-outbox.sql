-- Community-owned transactional outbox for searchable post snapshots.
SET NAMES utf8mb4;

CREATE TABLE community_search_outbox (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_type VARCHAR(64) NOT NULL,
  event_key VARCHAR(255) NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id BIGINT NOT NULL,
  payload JSON NOT NULL,
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
  UNIQUE KEY uk_community_search_outbox_type_key (event_type, event_key),
  KEY idx_community_search_outbox_publish (status, next_retry_time, id),
  KEY idx_community_search_outbox_aggregate (aggregate_type, aggregate_id, id),
  CONSTRAINT chk_community_search_outbox_status CHECK (status IN (10, 20, 30)),
  CONSTRAINT chk_community_search_outbox_attempts CHECK (attempts >= 0),
  CONSTRAINT chk_community_search_outbox_payload CHECK (JSON_VALID(payload))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Transactional community search projection events';

SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'community_search_outbox'
ORDER BY ordinal_position;

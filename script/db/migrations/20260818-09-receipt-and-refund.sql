-- Curmerce buyer receipt confirmation and basic simulated refunds.
-- MySQL 8.0.16+ is required. Review the preflight results before applying.
SET NAMES utf8mb4;

SELECT VERSION() AS mysql_version;
SELECT COUNT(*) AS invalid_existing_order_status_count
FROM commerce_order
WHERE status NOT IN (10, 20, 30, 40);
SELECT COUNT(*) AS existing_refund_table_count
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'commerce_refund';

ALTER TABLE commerce_order
  ADD COLUMN completion_time DATETIME NULL AFTER tracking_no;

CREATE TABLE commerce_refund (
    id BIGINT NOT NULL AUTO_INCREMENT,
    refund_no VARCHAR(40) NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(40) NOT NULL,
    member_user_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    status TINYINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    requested_time DATETIME NOT NULL,
    processed_time DATETIME NULL,
    creator VARCHAR(64) DEFAULT '',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64) DEFAULT '',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BIT NOT NULL DEFAULT b'0',
    PRIMARY KEY (id),
    UNIQUE KEY uk_commerce_refund_refund_no (refund_no),
    UNIQUE KEY uk_commerce_refund_order (order_id),
    KEY idx_commerce_refund_member_status_time (member_user_id, status, create_time, id),
    CONSTRAINT fk_commerce_refund_order FOREIGN KEY (order_id)
      REFERENCES commerce_order (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_commerce_refund_status CHECK (status IN (10, 20, 30, 40)),
    CONSTRAINT chk_commerce_refund_amount CHECK (amount >= 0),
    CONSTRAINT chk_commerce_refund_reason CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 255),
    CONSTRAINT chk_commerce_refund_processed_time CHECK (
      (status IN (10, 20) AND processed_time IS NULL)
      OR (status IN (30, 40) AND processed_time IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Post-apply checks.
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'commerce_order'
  AND column_name = 'completion_time';
SELECT table_name, table_rows
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name = 'commerce_refund';
SELECT index_name, column_name, non_unique
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'commerce_refund'
  AND index_name IN ('uk_commerce_refund_refund_no', 'uk_commerce_refund_order',
                     'idx_commerce_refund_member_status_time')
ORDER BY index_name, seq_in_index;

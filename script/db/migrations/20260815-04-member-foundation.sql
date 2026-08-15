-- Curmerce buyer identity and shipping-address foundation.
-- Review every preflight result before applying. MySQL 8.0.16+ is required.
SET NAMES utf8mb4;

SELECT VERSION() AS mysql_version;
SELECT table_name FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name IN ('member_user', 'member_address');

CREATE TABLE member_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  mobile VARCHAR(11) NOT NULL,
  password VARCHAR(100) NOT NULL,
  nickname VARCHAR(30) NOT NULL,
  avatar VARCHAR(1024) NULL,
  email VARCHAR(254) NULL,
  sex TINYINT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  register_ip VARCHAR(64) NULL,
  login_ip VARCHAR(64) NULL,
  login_date DATETIME NULL,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), UNIQUE KEY uk_member_user_mobile (mobile),
  CONSTRAINT chk_member_user_mobile CHECK (CHAR_LENGTH(TRIM(mobile)) BETWEEN 7 AND 11),
  CONSTRAINT chk_member_user_password CHECK (CHAR_LENGTH(TRIM(password)) >= 20),
  CONSTRAINT chk_member_user_nickname CHECK (CHAR_LENGTH(TRIM(nickname)) BETWEEN 2 AND 30),
  CONSTRAINT chk_member_user_status CHECK (status IN (0, 1)),
  CONSTRAINT chk_member_user_sex CHECK (sex IS NULL OR sex IN (0, 1, 2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Curmerce buyer member';

CREATE TABLE member_address (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  name VARCHAR(30) NOT NULL,
  mobile VARCHAR(11) NOT NULL,
  area_id INT NOT NULL,
  detail_address VARCHAR(255) NOT NULL,
  default_status TINYINT(1) NOT NULL DEFAULT 0,
  default_marker TINYINT NULL,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), UNIQUE KEY uk_member_address_default (user_id, default_marker),
  KEY idx_member_address_user_deleted_id (user_id, deleted, id),
  CONSTRAINT fk_member_address_user FOREIGN KEY (user_id) REFERENCES member_user(id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT chk_member_address_default CHECK (
    (deleted = b'1' AND default_status = 0 AND default_marker IS NULL)
    OR (deleted = b'0' AND ((default_status = 1 AND default_marker = 1) OR (default_status = 0 AND default_marker IS NULL)))
  ),
  CONSTRAINT chk_member_address_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 30),
  CONSTRAINT chk_member_address_detail CHECK (CHAR_LENGTH(TRIM(detail_address)) BETWEEN 1 AND 255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Curmerce buyer shipping address';

SELECT table_name, column_name, data_type FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name IN ('member_user', 'member_address')
ORDER BY table_name, ordinal_position;
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics WHERE table_schema = DATABASE()
AND table_name IN ('member_user', 'member_address') GROUP BY table_name, index_name;

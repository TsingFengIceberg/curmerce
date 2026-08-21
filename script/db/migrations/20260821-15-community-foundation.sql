-- Curmerce community foundation: posts, topics, comments, reactions, follows and reports.
-- MySQL 8.0.16+ is required. The community module owns these tables; product data
-- remains owned by commerce_product and is checked through its application API.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS community_post (
  id BIGINT NOT NULL AUTO_INCREMENT,
  author_user_id BIGINT NOT NULL,
  title VARCHAR(120) NOT NULL,
  content TEXT NOT NULL,
  media_urls JSON NULL,
  status TINYINT NOT NULL DEFAULT 0,
  like_count INT NOT NULL DEFAULT 0,
  favorite_count INT NOT NULL DEFAULT 0,
  comment_count INT NOT NULL DEFAULT 0,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id),
  KEY idx_community_post_feed (status, deleted, id),
  KEY idx_community_post_author (author_user_id, status, deleted, id),
  CONSTRAINT chk_community_post_status CHECK (status IN (0, 1, 2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Curmerce community post';

CREATE TABLE IF NOT EXISTS community_post_product (
  id BIGINT NOT NULL AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  sort INT NOT NULL DEFAULT 0,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), UNIQUE KEY uk_community_post_product (post_id, product_id, deleted),
  KEY idx_community_post_product_product (product_id, deleted, post_id),
  CONSTRAINT fk_community_post_product_post FOREIGN KEY (post_id) REFERENCES community_post(id)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Community post product association';

CREATE TABLE IF NOT EXISTS community_topic (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  slug VARCHAR(160) NOT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), UNIQUE KEY uk_community_topic_slug (slug),
  KEY idx_community_topic_status (status, deleted, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Community topic';

CREATE TABLE IF NOT EXISTS community_post_topic (
  id BIGINT NOT NULL AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  topic_id BIGINT NOT NULL,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), UNIQUE KEY uk_community_post_topic (post_id, topic_id, deleted),
  KEY idx_community_post_topic_topic (topic_id, deleted, post_id),
  CONSTRAINT fk_community_post_topic_post FOREIGN KEY (post_id) REFERENCES community_post(id)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_community_post_topic_topic FOREIGN KEY (topic_id) REFERENCES community_topic(id)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Community post topic association';

CREATE TABLE IF NOT EXISTS community_comment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  parent_id BIGINT NULL,
  author_user_id BIGINT NOT NULL,
  content VARCHAR(2000) NOT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), KEY idx_community_comment_post (post_id, status, deleted, id),
  KEY idx_community_comment_parent (parent_id, deleted, id),
  CONSTRAINT chk_community_comment_status CHECK (status IN (0, 1)),
  CONSTRAINT fk_community_comment_post FOREIGN KEY (post_id) REFERENCES community_post(id)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Community comment';

CREATE TABLE IF NOT EXISTS community_post_reaction (
  id BIGINT NOT NULL AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  type TINYINT NOT NULL,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), UNIQUE KEY uk_community_post_reaction (post_id, user_id, type, deleted),
  KEY idx_community_reaction_user (user_id, type, deleted, id),
  CONSTRAINT chk_community_reaction_type CHECK (type IN (1, 2)),
  CONSTRAINT fk_community_reaction_post FOREIGN KEY (post_id) REFERENCES community_post(id)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Community post like or favorite';

CREATE TABLE IF NOT EXISTS community_follow (
  id BIGINT NOT NULL AUTO_INCREMENT,
  follower_user_id BIGINT NOT NULL,
  followed_user_id BIGINT NOT NULL,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), UNIQUE KEY uk_community_follow (follower_user_id, followed_user_id, deleted),
  KEY idx_community_followed (followed_user_id, deleted, id),
  CONSTRAINT chk_community_follow_distinct CHECK (follower_user_id <> followed_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Community user follow';

CREATE TABLE IF NOT EXISTS community_report (
  id BIGINT NOT NULL AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  reporter_user_id BIGINT NOT NULL,
  reason VARCHAR(500) NOT NULL,
  status TINYINT NOT NULL DEFAULT 0,
  reviewer_user_id BIGINT NULL,
  review_remark VARCHAR(500) NULL,
  review_time DATETIME NULL,
  creator VARCHAR(64) DEFAULT '', create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64) DEFAULT '', update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (id), KEY idx_community_report_status (status, deleted, id),
  KEY idx_community_report_post (post_id, status, deleted, id),
  CONSTRAINT chk_community_report_status CHECK (status IN (0, 1, 2)),
  CONSTRAINT fk_community_report_post FOREIGN KEY (post_id) REFERENCES community_post(id)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Community content report';

INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, status, visible,
  keep_alive, always_show, creator, updater, deleted)
SELECT v.name, v.permission, 3, v.sort, 0, '', '#', '', 0, b'1', b'1', b'1', 'migration', 'migration', b'0'
FROM (SELECT 'Community post query' name, 'community:post:query' permission, 10 sort
      UNION ALL SELECT 'Community post audit', 'community:post:audit', 11
      UNION ALL SELECT 'Community report query', 'community:report:query', 20
      UNION ALL SELECT 'Community report audit', 'community:report:audit', 21) v
WHERE NOT EXISTS (SELECT 1 FROM system_menu m WHERE m.permission = v.permission AND m.deleted = b'0');

INSERT INTO system_role_menu (role_id, menu_id, creator, updater, deleted, tenant_id)
SELECT r.id, m.id, 'migration', 'migration', b'0', 0
FROM system_role r JOIN system_menu m ON m.permission IN
 ('community:post:query', 'community:post:audit', 'community:report:query', 'community:report:audit')
WHERE r.code = 'super_admin' AND r.tenant_id = 0 AND r.deleted = b'0' AND m.deleted = b'0'
  AND NOT EXISTS (SELECT 1 FROM system_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = m.id AND rm.deleted = b'0');

SELECT table_name, table_rows FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name LIKE 'community_%' ORDER BY table_name;

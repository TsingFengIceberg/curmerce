-- Curmerce media asset foundation.
-- Apply after 20260826-21-product-operation-log.sql.
-- Stop when either preflight query returns a row. Back up infra_file and
-- infra_file_content before applying because MySQL DDL auto-commits.

SELECT table_name AS conflicting_table
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('infra_file_reference', 'infra_media_upload_quota',
                     'infra_media_upload_ticket', 'infra_media_migration');

SELECT column_name AS conflicting_column
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'infra_file'
  AND column_name IN ('asset_key', 'sha256', 'dedup_key', 'asset_status', 'scan_status',
                      'moderation_status', 'moderation_reason', 'moderated_by', 'moderated_at', 'visibility',
                      'owner_user_id', 'owner_user_type', 'width', 'height',
                      'original_file_id', 'variant_name', 'bound_once', 'orphaned_at', 'last_access_time',
                      'failure_reason');

ALTER TABLE infra_file
  MODIFY COLUMN size BIGINT NOT NULL COMMENT '文件大小（字节）',
  ADD COLUMN asset_key CHAR(36) NULL COMMENT '稳定且不可枚举的资产标识' AFTER id,
  ADD COLUMN sha256 CHAR(64) NULL COMMENT '内容 SHA-256' AFTER size,
  ADD COLUMN dedup_key VARCHAR(128) NULL COMMENT '所有者范围内的并发去重键' AFTER sha256,
  ADD COLUMN asset_status TINYINT NOT NULL DEFAULT 10 COMMENT '资产状态：0处理中，10可用，20隔离，30失败' AFTER dedup_key,
  ADD COLUMN scan_status TINYINT NOT NULL DEFAULT 0 COMMENT '扫描状态：0待扫描，10通过，20拒绝，30跳过' AFTER asset_status,
  ADD COLUMN moderation_status TINYINT NOT NULL DEFAULT 50 COMMENT '审核状态：0待审核，10安全，20人工复核，30拒绝，40异常，50跳过' AFTER scan_status,
  ADD COLUMN moderation_reason VARCHAR(512) NULL COMMENT '审核原因' AFTER moderation_status,
  ADD COLUMN moderated_by BIGINT NULL COMMENT '最后人工审核管理员编号' AFTER moderation_reason,
  ADD COLUMN moderated_at DATETIME NULL COMMENT '最后审核时间' AFTER moderated_by,
  ADD COLUMN visibility TINYINT NOT NULL DEFAULT 0 COMMENT '可见性：0公开，10私有' AFTER moderated_at,
  ADD COLUMN owner_user_id BIGINT NULL COMMENT '上传用户编号' AFTER visibility,
  ADD COLUMN owner_user_type TINYINT NULL COMMENT '上传用户类型：1会员，2管理员' AFTER owner_user_id,
  ADD COLUMN width INT NULL COMMENT '图片宽度' AFTER owner_user_type,
  ADD COLUMN height INT NULL COMMENT '图片高度' AFTER width,
  ADD COLUMN original_file_id BIGINT NULL COMMENT '原始资产编号；衍生图使用' AFTER height,
  ADD COLUMN variant_name VARCHAR(32) NULL COMMENT '衍生版本名称' AFTER original_file_id,
  ADD COLUMN bound_once BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否曾绑定业务对象' AFTER variant_name,
  ADD COLUMN orphaned_at DATETIME NULL COMMENT '最后一次失去业务引用的时间' AFTER bound_once,
  ADD COLUMN last_access_time DATETIME NULL COMMENT '最近访问时间（按小时采样）' AFTER orphaned_at,
  ADD COLUMN failure_reason VARCHAR(512) NULL COMMENT '扫描或衍生失败原因' AFTER last_access_time;

UPDATE infra_file
SET asset_key = UUID(),
    asset_status = 10,
    scan_status = 30,
    moderation_status = 50,
    visibility = 0,
    orphaned_at = create_time
WHERE asset_key IS NULL;

ALTER TABLE infra_file
  MODIFY COLUMN asset_key CHAR(36) NOT NULL COMMENT '稳定且不可枚举的资产标识',
  ADD UNIQUE KEY uk_infra_file_asset_key (asset_key),
  ADD UNIQUE KEY uk_infra_file_dedup_key (dedup_key),
  ADD KEY idx_infra_file_sha256_status (sha256, asset_status, deleted),
  ADD KEY idx_infra_file_orphan (orphaned_at, asset_status, deleted),
  ADD UNIQUE KEY uk_infra_file_original_variant (original_file_id, variant_name),
  ADD CONSTRAINT fk_infra_file_original
    FOREIGN KEY (original_file_id) REFERENCES infra_file (id);

CREATE TABLE infra_file_reference (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '引用编号',
  file_id BIGINT NOT NULL COMMENT '文件资产编号',
  business_type VARCHAR(64) NOT NULL COMMENT '业务对象类型',
  business_id VARCHAR(64) NOT NULL COMMENT '业务对象编号',
  field_name VARCHAR(64) NOT NULL DEFAULT 'media' COMMENT '业务字段',
  creator VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_infra_file_reference (file_id, business_type, business_id, field_name, deleted),
  KEY idx_infra_file_reference_business (business_type, business_id, deleted),
  CONSTRAINT fk_infra_file_reference_file FOREIGN KEY (file_id) REFERENCES infra_file (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件业务引用表';

CREATE TABLE infra_media_upload_quota (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '配额记录编号',
  owner_user_id BIGINT NOT NULL COMMENT '上传用户编号',
  owner_user_type TINYINT NOT NULL COMMENT '上传用户类型：1会员，2管理员',
  quota_date DATE NOT NULL COMMENT '自然日（服务器时区）',
  upload_count INT NOT NULL DEFAULT 0 COMMENT '当日已接受上传次数',
  upload_bytes BIGINT NOT NULL DEFAULT 0 COMMENT '当日已接受上传字节数',
  reserved_storage_bytes BIGINT NOT NULL DEFAULT 0 COMMENT '已预留但尚未转为资产的字节数',
  creator VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_infra_media_quota_owner_date (owner_user_id, owner_user_type, quota_date, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='媒体上传配额账本';

CREATE TABLE infra_media_upload_ticket (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '票据编号',
  ticket_key CHAR(36) NOT NULL COMMENT '不可枚举票据标识',
  asset_key CHAR(36) NOT NULL COMMENT '预分配资产标识',
  config_id BIGINT NOT NULL COMMENT '目标文件配置编号',
  path VARCHAR(512) NOT NULL COMMENT '受约束对象路径',
  original_name VARCHAR(256) NOT NULL COMMENT '原始文件名',
  expected_type VARCHAR(63) NOT NULL COMMENT '声明 MIME 类型',
  expected_size BIGINT NOT NULL COMMENT '声明字节数',
  directory VARCHAR(128) NULL COMMENT '业务目录',
  visibility TINYINT NOT NULL DEFAULT 0 COMMENT '可见性：0公开，10私有',
  owner_user_id BIGINT NOT NULL COMMENT '申请用户编号',
  owner_user_type TINYINT NOT NULL COMMENT '申请用户类型',
  quota_date DATE NOT NULL COMMENT '配额记账日期',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0待上传，5确认中，10完成，20过期，30拒绝',
  expires_at DATETIME NOT NULL COMMENT '票据失效时间',
  finalized_file_id BIGINT NULL COMMENT '最终文件资产编号',
  failure_reason VARCHAR(512) NULL COMMENT '拒绝原因',
  creator VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_infra_media_ticket_key (ticket_key),
  UNIQUE KEY uk_infra_media_ticket_asset (asset_key),
  KEY idx_infra_media_ticket_expiry (status, expires_at, deleted),
  CONSTRAINT fk_infra_media_ticket_file FOREIGN KEY (finalized_file_id) REFERENCES infra_file (id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='媒体预签名上传票据';

CREATE TABLE infra_media_migration (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '迁移记录编号',
  file_id BIGINT NULL COMMENT '文件资产编号；资产清理后保留迁移审计',
  source_config_id BIGINT NOT NULL COMMENT '源文件配置编号',
  target_config_id BIGINT NOT NULL COMMENT '目标文件配置编号',
  source_path VARCHAR(512) NOT NULL COMMENT '源对象路径',
  target_path VARCHAR(512) NOT NULL COMMENT '目标对象路径',
  sha256 CHAR(64) NULL COMMENT '迁移时校验的内容 SHA-256',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0待处理，5处理中，10已复制，20已切换，30失败',
  attempt_count INT NOT NULL DEFAULT 0 COMMENT '尝试次数',
  last_error VARCHAR(1000) NULL COMMENT '最近失败原因',
  copied_at DATETIME NULL COMMENT '复制并校验完成时间',
  switched_at DATETIME NULL COMMENT '元数据切换时间',
  creator VARCHAR(64) DEFAULT '' COMMENT '创建者',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater VARCHAR(64) DEFAULT '' COMMENT '更新者',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_infra_media_migration_file_target (file_id, target_config_id, deleted),
  KEY idx_infra_media_migration_status (status, id, deleted),
  CONSTRAINT fk_infra_media_migration_file FOREIGN KEY (file_id) REFERENCES infra_file (id)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='媒体对象存储迁移审计表';

-- Post-apply verification. Each query must return the named objects.
SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'infra_file'
  AND column_name IN ('asset_key', 'sha256', 'dedup_key', 'asset_status', 'scan_status',
                      'moderation_status', 'moderation_reason', 'moderated_by', 'moderated_at', 'visibility',
                      'owner_user_id', 'owner_user_type', 'width', 'height',
                      'original_file_id', 'variant_name', 'bound_once', 'orphaned_at', 'last_access_time',
                      'failure_reason')
ORDER BY ordinal_position;

SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('infra_file', 'infra_file_reference', 'infra_media_upload_quota',
                     'infra_media_upload_ticket', 'infra_media_migration')
  AND index_name IN ('uk_infra_file_asset_key', 'uk_infra_file_dedup_key', 'idx_infra_file_sha256_status',
                     'idx_infra_file_orphan', 'uk_infra_file_original_variant',
                     'uk_infra_file_reference', 'idx_infra_file_reference_business',
                     'uk_infra_media_quota_owner_date', 'uk_infra_media_ticket_key',
                     'uk_infra_media_ticket_asset', 'idx_infra_media_ticket_expiry',
                     'uk_infra_media_migration_file_target', 'idx_infra_media_migration_status')
GROUP BY table_name, index_name
ORDER BY table_name, index_name;

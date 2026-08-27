-- Review aid for disposable local databases only.
-- Refuse to roll back while business references or derived assets exist.

SELECT COUNT(*) AS blocking_reference_count
FROM infra_file_reference
WHERE deleted = b'0';

SELECT COUNT(*) AS blocking_variant_count
FROM infra_file
WHERE deleted = b'0'
  AND original_file_id IS NOT NULL;

SELECT COUNT(*) AS blocking_quota_count
FROM infra_media_upload_quota
WHERE deleted = b'0'
  AND (upload_count > 0 OR upload_bytes > 0 OR reserved_storage_bytes > 0);

SELECT COUNT(*) AS blocking_upload_ticket_count
FROM infra_media_upload_ticket
WHERE deleted = b'0';

SELECT COUNT(*) AS blocking_migration_count
FROM infra_media_migration
WHERE deleted = b'0';

-- After all five counts are zero, an operator may explicitly run the statements
-- below in a disposable database. They remain commented to prevent accidental
-- media metadata loss.
-- DROP TABLE infra_media_upload_ticket;
-- DROP TABLE infra_media_migration;
-- DROP TABLE infra_file_reference;
-- DROP TABLE infra_media_upload_quota;
-- ALTER TABLE infra_file DROP FOREIGN KEY fk_infra_file_original;
-- ALTER TABLE infra_file
--   DROP INDEX uk_infra_file_asset_key,
--   DROP INDEX uk_infra_file_dedup_key,
--   DROP INDEX idx_infra_file_sha256_status,
--   DROP INDEX idx_infra_file_orphan,
--   DROP INDEX uk_infra_file_original_variant,
--   DROP COLUMN asset_key,
--   DROP COLUMN sha256,
--   DROP COLUMN dedup_key,
--   DROP COLUMN asset_status,
--   DROP COLUMN scan_status,
--   DROP COLUMN moderation_status,
--   DROP COLUMN moderation_reason,
--   DROP COLUMN moderated_by,
--   DROP COLUMN moderated_at,
--   DROP COLUMN visibility,
--   DROP COLUMN owner_user_id,
--   DROP COLUMN owner_user_type,
--   DROP COLUMN width,
--   DROP COLUMN height,
--   DROP COLUMN original_file_id,
--   DROP COLUMN variant_name,
--   DROP COLUMN bound_once,
--   DROP COLUMN orphaned_at,
--   DROP COLUMN last_access_time,
--   DROP COLUMN failure_reason;

-- Forward-compatible repair for installations that created the reservation
-- ledger before tenant ownership was added.  This migration is intentionally
-- additive and safe to run more than once; it never rewrites or deletes
-- reservation rows.
SET NAMES utf8mb4;
SET @db = DATABASE();

SET @table_exists = (SELECT COUNT(*) FROM information_schema.tables
                     WHERE table_schema = @db
                       AND table_name = 'commerce_release_reservation');

SET @sql = IF(@table_exists = 1 AND
              (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = @db
                 AND table_name = 'commerce_release_reservation'
                 AND column_name = 'tenant_id') = 0,
  'ALTER TABLE commerce_release_reservation ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT ''default'' AFTER id',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- Replace the pre-tenant identity key only when it exists.  Keeping the
-- original key would make two tenants with the same business identity
-- collide during a retry, while the tenant-scoped key preserves all rows.
SET @sql = IF(@table_exists = 1 AND
              (SELECT COUNT(*) FROM information_schema.statistics
               WHERE table_schema = @db
                 AND table_name = 'commerce_release_reservation'
                 AND index_name = 'uk_release_reservation_identity') = 1 AND
              (SELECT COUNT(*) FROM information_schema.columns
               WHERE table_schema = @db
                 AND table_name = 'commerce_release_reservation'
                 AND column_name = 'tenant_id') = 1,
  'ALTER TABLE commerce_release_reservation DROP INDEX uk_release_reservation_identity',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(@table_exists = 1 AND
              (SELECT COUNT(*) FROM information_schema.statistics
               WHERE table_schema = @db
                 AND table_name = 'commerce_release_reservation'
                 AND index_name = 'uk_release_reservation_identity') = 0,
  'ALTER TABLE commerce_release_reservation ADD UNIQUE KEY uk_release_reservation_identity (tenant_id, campaign_id, item_id, buyer_user_id, reservation_key, deleted)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF(@table_exists = 1 AND
              (SELECT COUNT(*) FROM information_schema.statistics
               WHERE table_schema = @db
                 AND table_name = 'commerce_release_reservation'
                 AND index_name = 'idx_release_reservation_tenant_status') = 0,
  'ALTER TABLE commerce_release_reservation ADD KEY idx_release_reservation_tenant_status (tenant_id, status, id)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SELECT table_name, column_name, data_type, column_default
FROM information_schema.columns
WHERE table_schema = @db
  AND table_name = 'commerce_release_reservation'
  AND column_name = 'tenant_id';
SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics
WHERE table_schema = @db
  AND table_name = 'commerce_release_reservation'
  AND index_name IN ('uk_release_reservation_identity', 'idx_release_reservation_tenant_status')
GROUP BY index_name
ORDER BY index_name;

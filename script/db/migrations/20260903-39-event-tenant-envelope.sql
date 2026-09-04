-- Persist tenant ownership on every asynchronous commerce/community event.
-- Publishers run on scheduled worker threads, where request ThreadLocals are
-- unavailable; the tenant must therefore be captured with the Outbox row.
SET NAMES utf8mb4;

SET @db = DATABASE();

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db
               AND table_name='commerce_outbox_event' AND column_name='tenant_id') = 0,
  'ALTER TABLE commerce_outbox_event ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT ''default'' AFTER id',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=@db
               AND table_name='commerce_outbox_event' AND index_name='uk_commerce_outbox_event_type_key') = 1,
  'ALTER TABLE commerce_outbox_event DROP INDEX uk_commerce_outbox_event_type_key', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=@db
               AND table_name='commerce_outbox_event' AND index_name='uk_commerce_outbox_event_tenant_type_key') = 0,
  'ALTER TABLE commerce_outbox_event ADD UNIQUE KEY uk_commerce_outbox_event_tenant_type_key (tenant_id, event_type, event_key)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@db
               AND table_name='community_search_outbox' AND column_name='tenant_id') = 0,
  'ALTER TABLE community_search_outbox ADD COLUMN tenant_id VARCHAR(64) NOT NULL DEFAULT ''default'' AFTER id',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=@db
               AND table_name='community_search_outbox' AND index_name='uk_community_search_outbox_type_key') = 1,
  'ALTER TABLE community_search_outbox DROP INDEX uk_community_search_outbox_type_key', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=@db
               AND table_name='community_search_outbox' AND index_name='uk_community_search_outbox_tenant_type_key') = 0,
  'ALTER TABLE community_search_outbox ADD UNIQUE KEY uk_community_search_outbox_tenant_type_key (tenant_id, event_type, event_key)',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SELECT table_name, column_name, data_type, column_default
FROM information_schema.columns
WHERE table_schema=@db AND table_name IN ('commerce_outbox_event','community_search_outbox')
  AND column_name='tenant_id'
ORDER BY table_name;
SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS index_columns
FROM information_schema.statistics
WHERE table_schema=@db AND table_name IN ('commerce_outbox_event','community_search_outbox')
  AND index_name LIKE '%tenant_type_key'
GROUP BY table_name, index_name
ORDER BY table_name;

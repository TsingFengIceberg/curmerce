-- Move the Community-owned tables from the Core schema into an independently
-- permissioned schema. Run this migration through a MySQL administrative Unix
-- socket during a Community-service maintenance window.
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS curmerce_community
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

SELECT table_schema, table_name, table_rows
FROM information_schema.tables
WHERE table_schema IN (DATABASE(), 'curmerce_community')
  AND table_name IN ('community_post', 'community_post_product', 'community_topic',
                     'community_post_topic', 'community_comment', 'community_post_reaction',
                     'community_follow', 'community_report')
ORDER BY table_schema, table_name;

-- Stop if any target table already exists. RENAME TABLE is atomic: either all
-- eight ownership moves succeed, or none of them is applied.
RENAME TABLE
  community_post TO curmerce_community.community_post,
  community_post_product TO curmerce_community.community_post_product,
  community_topic TO curmerce_community.community_topic,
  community_post_topic TO curmerce_community.community_post_topic,
  community_comment TO curmerce_community.community_comment,
  community_post_reaction TO curmerce_community.community_post_reaction,
  community_follow TO curmerce_community.community_follow,
  community_report TO curmerce_community.community_report;

SELECT table_schema, table_name, table_rows
FROM information_schema.tables
WHERE table_schema IN (DATABASE(), 'curmerce_community')
  AND table_name LIKE 'community_%'
ORDER BY table_schema, table_name;

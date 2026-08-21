SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS community_report, community_post_reaction, community_follow, community_comment,
  community_post_topic, community_topic, community_post_product, community_post;
SET FOREIGN_KEY_CHECKS = 1;
DELETE FROM system_role_menu WHERE menu_id IN (SELECT id FROM system_menu WHERE permission LIKE 'community:%');
DELETE FROM system_menu WHERE permission LIKE 'community:%';

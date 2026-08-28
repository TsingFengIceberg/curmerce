-- Review aid only. Stop Community before moving its tables back to the Core
-- schema. This does not drop the Community database or its least-privilege user.
SET NAMES utf8mb4;

RENAME TABLE
  curmerce_community.community_post TO community_post,
  curmerce_community.community_post_product TO community_post_product,
  curmerce_community.community_topic TO community_topic,
  curmerce_community.community_post_topic TO community_post_topic,
  curmerce_community.community_comment TO community_comment,
  curmerce_community.community_post_reaction TO community_post_reaction,
  curmerce_community.community_follow TO community_follow,
  curmerce_community.community_report TO community_report;

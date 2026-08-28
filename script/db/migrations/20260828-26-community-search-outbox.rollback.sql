-- Review pending events before rollback; export them if the search index cannot be rebuilt.
SELECT COUNT(*) AS unfinished_count FROM community_search_outbox WHERE deleted = b'0' AND status <> 30;
DROP TABLE IF EXISTS community_search_outbox;

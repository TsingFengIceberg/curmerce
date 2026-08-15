-- Guarded review aid only. Do not run automatically.
SELECT table_name, table_rows FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_name IN ('member_user', 'member_address');
-- Refuse rollback when rows exist. After explicit review on a disposable database:
-- DROP TABLE member_address;
-- DROP TABLE member_user;

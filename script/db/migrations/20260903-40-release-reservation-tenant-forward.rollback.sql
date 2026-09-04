-- Diagnostic only.  Do not drop tenant_id or the tenant-scoped key while
-- non-default reservations exist.  A reviewed, environment-specific
-- rollback must export/drain those rows first and restore the old key shape.
SET NAMES utf8mb4;
SELECT COUNT(*) AS non_default_rows
FROM commerce_release_reservation
WHERE tenant_id <> 'default' AND deleted = b'0';
SELECT 'ABORT: this rollback file intentionally performs no destructive DDL' AS rollback_check;

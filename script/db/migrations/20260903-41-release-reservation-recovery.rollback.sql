-- Diagnostic only. Do not drop recovery columns or the DEAD status while
-- committed reservations exist. A reviewed forward migration is required
-- for any rollback because MySQL DDL auto-commits.
SET NAMES utf8mb4;
SELECT COUNT(*) AS dead_rows
FROM commerce_release_reservation
WHERE status = 40 AND deleted = b'0';
SELECT 'ABORT: this rollback file intentionally performs no destructive DDL' AS rollback_check;

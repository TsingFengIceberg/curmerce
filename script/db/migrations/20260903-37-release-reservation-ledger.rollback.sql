-- Roll back migration 37 only when no pending reservation finalization exists.
SET @pending_release_reservations = (
  SELECT COUNT(*) FROM commerce_release_reservation WHERE status = 20 AND deleted = b'0'
);
SET @drop_release_reservation_sql = IF(@pending_release_reservations = 0,
  'DROP TABLE IF EXISTS commerce_release_reservation',
  'SELECT 1');
PREPARE drop_release_reservation_stmt FROM @drop_release_reservation_sql;
EXECUTE drop_release_reservation_stmt;
DEALLOCATE PREPARE drop_release_reservation_stmt;

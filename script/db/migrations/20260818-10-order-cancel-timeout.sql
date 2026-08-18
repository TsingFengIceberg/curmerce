-- Curmerce pending-payment cancellation, payment deadlines, stock restoration,
-- and timeout-closing support. MySQL 8.0.16+ is required.
SET NAMES utf8mb4;

SELECT VERSION() AS mysql_version;
SELECT COUNT(*) AS invalid_existing_order_status_count
FROM commerce_order
WHERE status NOT IN (10, 20, 30, 40, 50);
SELECT COUNT(*) AS existing_canceled_order_count
FROM commerce_order
WHERE status = 50;

ALTER TABLE commerce_order
  ADD COLUMN payment_deadline DATETIME NULL AFTER status,
  DROP CHECK chk_commerce_order_status,
  ADD CONSTRAINT chk_commerce_order_status CHECK (status IN (10, 20, 30, 40, 50)),
  ADD KEY idx_commerce_order_pending_payment_deadline
    (status, payment_deadline, id);

ALTER TABLE commerce_payment
  DROP CHECK chk_commerce_payment_status,
  ADD CONSTRAINT chk_commerce_payment_status CHECK (status IN (10, 20, 30));

SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'commerce_order'
  AND column_name = 'payment_deadline';
SELECT constraint_name, check_clause
FROM information_schema.check_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name IN ('chk_commerce_order_status', 'chk_commerce_payment_status');

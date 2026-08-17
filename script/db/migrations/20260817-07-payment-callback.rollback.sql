-- Guarded review aid only; inspect payment rows and order state before rollback.
SELECT status, COUNT(*) AS order_count
FROM commerce_order
GROUP BY status
ORDER BY status;
SELECT COUNT(*) AS payment_count FROM commerce_payment;

-- Rollback is intentionally commented out. It is safe only when payment_count = 0
-- and no order has reached the paid/pending-shipment state.
-- ALTER TABLE commerce_order
--   DROP CHECK chk_commerce_order_status,
--   ADD CONSTRAINT chk_commerce_order_status CHECK (status IN (10));
-- DROP TABLE commerce_payment;

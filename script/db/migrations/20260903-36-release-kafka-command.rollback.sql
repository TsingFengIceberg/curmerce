-- Roll back only when CURMERCE_RELEASE_KAFKA_QUEUE_ENABLED=false and no
-- queued, processing, or retrying commands remain. Completed/failed command
-- history is operational evidence and should normally be retained.
SET NAMES utf8mb4;

SELECT status, COUNT(*) AS count
FROM commerce_release_purchase_command
WHERE deleted = b'0'
GROUP BY status;

-- Deliberately do not drop the table automatically. An operator must archive
-- or explicitly remove durable command history after confirming no active
-- command remains and no Kafka redelivery can arrive.

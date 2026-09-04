#!/usr/bin/env bash
set -Eeuo pipefail

# End-to-end acceptance for the Kafka-backed limited-release command queue.
# It delegates purchase contention and stock/idempotency assertions to the
# shared two-Core verifier, then proves the durable Kafka command queue drains
# to a terminal state. Run only against a disposable campaign and two Core
# instances configured with CURMERCE_OUTBOX_TRANSPORT=kafka and
# CURMERCE_RELEASE_KAFKA_QUEUE_ENABLED=true.
VERIFY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTANCE_A_URL="${CURMERCE_RELEASE_INSTANCE_A_URL:-http://127.0.0.1:48080}"
INSTANCE_B_URL="${CURMERCE_RELEASE_INSTANCE_B_URL:-http://127.0.0.1:48081}"
RELIABILITY_TOKEN="${CURMERCE_RELEASE_RELIABILITY_TOKEN:?set CURMERCE_RELEASE_RELIABILITY_TOKEN with commerce:reliability:query permission}"
POLL_SECONDS="${CURMERCE_RELEASE_KAFKA_DRAIN_POLL_SECONDS:-45}"

INSTANCE_A_URL="${INSTANCE_A_URL%/}"
INSTANCE_B_URL="${INSTANCE_B_URL%/}"
[[ "$INSTANCE_A_URL" != "$INSTANCE_B_URL" ]] || {
  echo "CURMERCE_RELEASE_INSTANCE_A_URL and CURMERCE_RELEASE_INSTANCE_B_URL must be different instances" >&2
  exit 2
}
[[ "$POLL_SECONDS" =~ ^[1-9][0-9]*$ ]] || { echo "CURMERCE_RELEASE_KAFKA_DRAIN_POLL_SECONDS must be positive" >&2; exit 2; }

kafka_status() {
  curl --fail --silent --show-error --max-time 10 \
    -H "Authorization: Bearer $RELIABILITY_TOKEN" -H 'tenant-id: 1' \
    "$1/app-api/commerce/release/purchase/async/kafka-status"
}

before="$(kafka_status "$INSTANCE_A_URL")"
printf '%s' "$before" | jq -e '.code == 0 and .data.enabled == true' >/dev/null || {
  echo "Kafka limited-release queue is not enabled on instance A" >&2
  exit 1
}
other="$(kafka_status "$INSTANCE_B_URL")"
printf '%s' "$other" | jq -e '.code == 0 and .data.enabled == true' >/dev/null || {
  echo "Kafka limited-release queue is not enabled on instance B" >&2
  exit 1
}

bash "$VERIFY_DIR/limited-release-multi-instance-smoke.sh"

deadline=$((SECONDS + POLL_SECONDS))
while (( SECONDS < deadline )); do
  snapshot="$(kafka_status "$INSTANCE_A_URL")"
  queued="$(jq -r '.data.queued // -1' <<<"$snapshot")"
  processing="$(jq -r '.data.processing // -1' <<<"$snapshot")"
  retry_waiting="$(jq -r '.data.retryWaiting // -1' <<<"$snapshot")"
  failed="$(jq -r '.data.failed // -1' <<<"$snapshot")"
  [[ "$queued" =~ ^[0-9]+$ && "$processing" =~ ^[0-9]+$ && "$retry_waiting" =~ ^[0-9]+$ && "$failed" =~ ^[0-9]+$ ]] || {
    echo "Kafka command status is unavailable" >&2
    exit 1
  }
  (( queued == 0 && processing == 0 && retry_waiting == 0 )) && break
  sleep 1
done

(( queued == 0 && processing == 0 && retry_waiting == 0 )) || {
  printf 'FAIL: Kafka queue did not drain; queued=%s processing=%s retryWaiting=%s failed=%s\n' \
    "$queued" "$processing" "$retry_waiting" "$failed" >&2
  exit 1
}
printf 'PASS: Kafka limited-release queue drained; failed_terminal_commands=%s\n' "$failed"

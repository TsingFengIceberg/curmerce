#!/usr/bin/env bash
set -Eeuo pipefail

# Validate the explicit local failure-injection contract. Start a disposable
# Core instance with CURMERCE_RELEASE_FAULT_INJECTION_POINT set to one of the
# points below, submit one request, then restart it with NONE and poll the same
# ticket. The script never changes a production process or prints a token.
#
# A post-commit fault must recover to COMPLETED. In particular, ACK proves that
# a committed order is not duplicated when a Redis Stream acknowledgement is
# lost. Set MYSQL_DEFAULTS_FILE and Redis connection settings to make the
# script verify the single order and MySQL/Redis inventory convergence.
BASE_URL="${CURMERCE_CORE_URL:-http://127.0.0.1:48080}"
TOKEN="${CURMERCE_TEST_TOKEN:?set CURMERCE_TEST_TOKEN to a disposable user token}"
ITEM_ID="${CURMERCE_RELEASE_ITEM_ID:?set CURMERCE_RELEASE_ITEM_ID}"
ADDRESS_ID="${CURMERCE_RELEASE_ADDRESS_ID:?set CURMERCE_RELEASE_ADDRESS_ID}"
POINT="${CURMERCE_RELEASE_FAULT_INJECTION_POINT:?set CURMERCE_RELEASE_FAULT_INJECTION_POINT}"
POLL_SECONDS="${CURMERCE_RELEASE_POLL_SECONDS:-45}"
EXPECTED_STATUS="${CURMERCE_RELEASE_EXPECT_STATUS:-COMPLETED}"
VERIFY_DATABASE="${CURMERCE_RELEASE_VERIFY_DATABASE:-true}"
VERIFY_REDIS="${CURMERCE_RELEASE_VERIFY_REDIS:-true}"
IDEMPOTENCY_KEY="${CURMERCE_RELEASE_FAULT_IDEMPOTENCY_KEY:-}"
# An operator may provide a restart command for a disposable instance. The
# verifier never stops a process on its own; the hook is deliberately opt-in
# and receives the ticket and idempotency key through its environment.
RESTART_HOOK="${CURMERCE_RELEASE_RESTART_HOOK:-}"
RESTART_DELAY_SECONDS="${CURMERCE_RELEASE_RESTART_DELAY_SECONDS:-0}"
MYSQL_CLI="${MYSQL_CLI:-mysql}"
MYSQL_DEFAULTS_FILE="${MYSQL_DEFAULTS_FILE:-}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${MYSQL_PWD:-}}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-13306}"
MYSQL_DATABASE="${MYSQL_DATABASE:-curmerce}"
MYSQL_USER="${MYSQL_USER:-curmerce}"
REDIS_CLI="${REDIS_CLI:-redis-cli}"
REDIS_HOST="${CURMERCE_REDIS_HOST:-${REDIS_HOST:-127.0.0.1}}"
REDIS_PORT="${CURMERCE_REDIS_PORT:-${REDIS_PORT:-16379}}"
REDIS_DB="${CURMERCE_REDIS_DB:-${REDIS_DB:-0}}"
REDIS_USER="${CURMERCE_REDIS_USER:-${REDIS_USER:-}}"
REDIS_PASSWORD="${CURMERCE_REDIS_PASSWORD:-${REDIS_PASSWORD:-}}"

case "$POINT" in
  ENQUEUE|BEFORE_PURCHASE|AFTER_COMMIT_STATUS|ACK|DEAD_LETTER) ;;
  *) echo "invalid fault point: $POINT" >&2; exit 2 ;;
esac
case "$EXPECTED_STATUS" in
  COMPLETED|FAILED) ;;
  *) echo "invalid expected status: $EXPECTED_STATUS" >&2; exit 2 ;;
esac
(( POLL_SECONDS > 0 )) || { echo "poll timeout must be positive" >&2; exit 2; }
[[ "$RESTART_DELAY_SECONDS" =~ ^[0-9]+$ ]] || {
  echo "CURMERCE_RELEASE_RESTART_DELAY_SECONDS must be a non-negative integer" >&2; exit 2;
}
[[ "$VERIFY_DATABASE" == true || "$VERIFY_DATABASE" == false ]] || {
  echo "CURMERCE_RELEASE_VERIFY_DATABASE must be true or false" >&2; exit 2;
}
[[ "$VERIFY_REDIS" == true || "$VERIFY_REDIS" == false ]] || {
  echo "CURMERCE_RELEASE_VERIFY_REDIS must be true or false" >&2; exit 2;
}

if [[ "$VERIFY_DATABASE" == true ]]; then
  if [[ -n "$MYSQL_DEFAULTS_FILE" ]]; then
    [[ -f "$MYSQL_DEFAULTS_FILE" ]] || {
      echo "MYSQL_DEFAULTS_FILE does not exist: $MYSQL_DEFAULTS_FILE" >&2; exit 2;
    }
  elif [[ -z "$MYSQL_PASSWORD" ]]; then
    echo "set MYSQL_DEFAULTS_FILE or MYSQL_PASSWORD/MYSQL_PWD for database verification" >&2; exit 2
  fi
fi
if [[ "$VERIFY_REDIS" == true && "$VERIFY_DATABASE" != true ]]; then
  echo "Redis reconciliation verification also requires database verification" >&2; exit 2
fi

mysql_args=(--batch --skip-column-names --user="$MYSQL_USER")
if [[ -n "$MYSQL_DEFAULTS_FILE" ]]; then mysql_args+=(--defaults-extra-file="$MYSQL_DEFAULTS_FILE"); fi
if [[ -n "${MYSQL_SOCKET:-}" ]]; then mysql_args+=(--socket="$MYSQL_SOCKET"); else mysql_args+=(--host="$MYSQL_HOST" --port="$MYSQL_PORT"); fi
mysql_query() { MYSQL_PWD="$MYSQL_PASSWORD" "$MYSQL_CLI" "${mysql_args[@]}" "$MYSQL_DATABASE" -e "$1"; }

redis_args=(-h "$REDIS_HOST" -p "$REDIS_PORT" -n "$REDIS_DB" --raw)
if [[ -n "$REDIS_USER" ]]; then redis_args+=(--user "$REDIS_USER"); fi
redis() {
  if [[ -n "$REDIS_PASSWORD" ]]; then
    REDISCLI_AUTH="$REDIS_PASSWORD" "$REDIS_CLI" "${redis_args[@]}" "$@"
  else
    "$REDIS_CLI" "${redis_args[@]}" "$@"
  fi
}

safe_sql() { printf '%s' "$1" | sed "s/'/''/g"; }
is_positive_integer() { [[ "$1" =~ ^[1-9][0-9]*$ ]]; }

echo "Fault point $POINT must be enabled only on a disposable instance."
echo "1. Start Core with CURMERCE_RELEASE_FAULT_INJECTION_POINT=$POINT and fail-once=true."
echo "2. Submit one async purchase and record the returned ticket."
echo "3. Stop that instance, restart with CURMERCE_RELEASE_FAULT_INJECTION_POINT=NONE."
echo "4. Poll the ticket below; it must reach $EXPECTED_STATUS without overselling or duplicate order creation."

TICKET="${CURMERCE_RELEASE_FAULT_INJECTION_TICKET:-}"
if [[ -z "$TICKET" ]]; then
  nonce="$(date +%s%N)"
  IDEMPOTENCY_KEY="${IDEMPOTENCY_KEY:-fault-drill-$nonce}"
  body="{\"itemId\":$ITEM_ID,\"addressId\":$ADDRESS_ID,\"quantity\":1,\"idempotencyKey\":\"$IDEMPOTENCY_KEY\"}"
  TICKET="$(curl --fail --silent --show-error --max-time 10 \
    -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -H 'tenant-id: 1' \
    -X POST "$BASE_URL/app-api/commerce/release/purchase/async" -d "$body" | jq -r '.data // empty')"
fi
[[ -n "$TICKET" ]] || { echo "no ticket returned" >&2; exit 1; }
if [[ "$VERIFY_DATABASE" == true ]]; then
  [[ -n "$IDEMPOTENCY_KEY" ]] || {
    echo "set CURMERCE_RELEASE_FAULT_IDEMPOTENCY_KEY when reusing a ticket" >&2; exit 2;
  }
fi

if [[ -n "$RESTART_HOOK" ]]; then
  # The hook is supplied by the operator and is intentionally not echoed,
  # because it may contain deployment-specific details. It must restore the
  # same disposable instance with fault injection disabled before returning.
  echo "Running the explicitly supplied disposable-instance restart hook."
  if (( RESTART_DELAY_SECONDS > 0 )); then sleep "$RESTART_DELAY_SECONDS"; fi
  (
    export CURMERCE_RELEASE_FAULT_TICKET="$TICKET"
    export CURMERCE_RELEASE_FAULT_IDEMPOTENCY_KEY="$IDEMPOTENCY_KEY"
    export CURMERCE_RELEASE_FAULT_INJECTION_POINT="$POINT"
    bash -c "$RESTART_HOOK"
  )
fi

deadline=$((SECONDS + POLL_SECONDS))
last="UNKNOWN"
last_response=''
while (( SECONDS < deadline )); do
  response="$(curl --silent --show-error --max-time 10 \
    -H "Authorization: Bearer $TOKEN" -H 'tenant-id: 1' \
    "$BASE_URL/app-api/commerce/release/purchase/async/status?ticket=$TICKET" || true)"
  last_response="$response"
  last="$(jq -r '.data.status // "UNKNOWN"' <<<"$response" 2>/dev/null || printf UNKNOWN)"
  case "$last" in
    COMPLETED|FAILED) break ;;
  esac
  sleep 1
done
[[ "$last" == "$EXPECTED_STATUS" ]] || {
  printf 'FAIL: fault point=%s expected status=%s but received=%s\n' "$POINT" "$EXPECTED_STATUS" "$last" >&2
  exit 1
}

if [[ "$EXPECTED_STATUS" == COMPLETED ]]; then
  purchase_id="$(jq -r '.data.result.purchaseId // empty' <<<"$last_response")"
  order_id="$(jq -r '.data.result.orderId // empty' <<<"$last_response")"
  is_positive_integer "$purchase_id" || { echo "FAIL: completed ticket has no purchase id" >&2; exit 1; }
  is_positive_integer "$order_id" || { echo "FAIL: completed ticket has no order id" >&2; exit 1; }
fi

if [[ "$VERIFY_DATABASE" == true ]]; then
  key_sql="$(safe_sql "$IDEMPOTENCY_KEY")"
  order_count="$(mysql_query "SELECT COUNT(*) FROM commerce_order WHERE idempotency_key = '$key_sql' AND deleted = b'0';")"
  if [[ "$EXPECTED_STATUS" == COMPLETED ]]; then
    purchase_count="$(mysql_query "SELECT COUNT(*) FROM commerce_release_purchase WHERE order_id = $order_id AND item_id = $ITEM_ID AND deleted = b'0';")"
    [[ "$order_count" == 1 ]] || { echo "FAIL: idempotency key produced $order_count orders, expected 1" >&2; exit 1; }
    [[ "$purchase_count" == 1 ]] || { echo "FAIL: order $order_id produced $purchase_count release purchases, expected 1" >&2; exit 1; }
  else
    [[ "$order_count" == 0 ]] || { echo "FAIL: failed ticket's idempotency key produced $order_count orders, expected 0" >&2; exit 1; }
  fi
fi

if [[ "$VERIFY_REDIS" == true ]]; then
  campaign_id="$(mysql_query "SELECT campaign_id FROM commerce_release_item WHERE id = $ITEM_ID AND deleted = b'0';")"
  database_stock="$(mysql_query "SELECT stock FROM commerce_release_item WHERE id = $ITEM_ID AND deleted = b'0';")"
  [[ "$campaign_id" =~ ^[1-9][0-9]*$ && "$database_stock" =~ ^[0-9]+$ ]] || {
    echo "FAIL: release item $ITEM_ID is unavailable for Redis reconciliation verification" >&2; exit 1;
  }
  redis_stock="$(redis GET "curmerce:release:v1:stock:$campaign_id:$ITEM_ID" || true)"
  redis_reserved="$(redis GET "curmerce:release:v1:reserved:$campaign_id:$ITEM_ID" || true)"
  [[ "$redis_stock" == "$database_stock" ]] || {
    echo "FAIL: Redis stock=$redis_stock does not match MySQL stock=$database_stock" >&2; exit 1;
  }
  [[ -z "$redis_reserved" || "$redis_reserved" == 0 ]] || {
    echo "FAIL: Redis reserved=$redis_reserved after terminal ticket" >&2; exit 1;
  }
fi

printf 'PASS: fault point=%s ticket=%s status=%s unique_order=%s redis_reconciled=%s\n' \
  "$POINT" "$TICKET" "$last" "$VERIFY_DATABASE" "$VERIFY_REDIS"

#!/usr/bin/env bash
set -Eeuo pipefail

# Exercises two Core HTTP instances that share the same MySQL and Redis. The
# script deliberately keeps the request payload disposable and never prints
# the bearer token. Start both instances with
# CURMERCE_RELEASE_DISTRIBUTED_QUEUE_ENABLED=true before running it.
INSTANCE_A_URL="${CURMERCE_RELEASE_INSTANCE_A_URL:-http://127.0.0.1:48080}"
INSTANCE_B_URL="${CURMERCE_RELEASE_INSTANCE_B_URL:-http://127.0.0.1:48081}"
# Supply one disposable token through CURMERCE_TEST_TOKEN, or a comma-separated
# set through CURMERCE_TEST_TOKENS to make stock contention use distinct buyers.
TOKENS_CSV="${CURMERCE_TEST_TOKENS:-${CURMERCE_TEST_TOKEN:-}}"
ITEM_ID="${CURMERCE_RELEASE_ITEM_ID:?set CURMERCE_RELEASE_ITEM_ID}"
# A single address remains supported. With multiple test buyers, provide a
# comma-separated CURMERCE_RELEASE_ADDRESS_IDS aligned with TEST_TOKENS.
ADDRESS_IDS_CSV="${CURMERCE_RELEASE_ADDRESS_IDS:-${CURMERCE_RELEASE_ADDRESS_ID:-}}"
STOCK="${CURMERCE_RELEASE_EXPECTED_STOCK:?set CURMERCE_RELEASE_EXPECTED_STOCK}"
ATTEMPTS="${CURMERCE_RELEASE_CONCURRENCY:-100}"
POLL_SECONDS="${CURMERCE_RELEASE_POLL_SECONDS:-30}"
EXPECT_EXACT_COMPLETED="${CURMERCE_RELEASE_EXPECT_EXACT_COMPLETED:-false}"
# The values below are intentionally opt-in: ordinary smoke runs must not
# require database credentials. BASE_STOCK is the stock before this disposable
# drill starts, not the expected stock after it ends.
VERIFY_DATABASE="${CURMERCE_RELEASE_VERIFY_DATABASE:-false}"
VERIFY_REDIS="${CURMERCE_RELEASE_VERIFY_REDIS:-false}"
BASE_STOCK="${CURMERCE_RELEASE_BASE_STOCK:-}"
RUN_ID="${CURMERCE_RELEASE_RUN_ID:-multi-instance-$(date +%s%N)}"
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

(( STOCK >= 0 && ATTEMPTS > 1 && POLL_SECONDS > 0 )) || {
  echo "invalid stock, concurrency, or poll timeout" >&2
  exit 2
}
INSTANCE_A_URL="${INSTANCE_A_URL%/}"
INSTANCE_B_URL="${INSTANCE_B_URL%/}"
[[ "$INSTANCE_A_URL" != "$INSTANCE_B_URL" ]] || {
  echo "CURMERCE_RELEASE_INSTANCE_A_URL and CURMERCE_RELEASE_INSTANCE_B_URL must be different instances" >&2
  exit 2
}
[[ -n "$TOKENS_CSV" ]] || { echo "set CURMERCE_TEST_TOKEN or CURMERCE_TEST_TOKENS" >&2; exit 2; }
[[ -n "$ADDRESS_IDS_CSV" ]] || { echo "set CURMERCE_RELEASE_ADDRESS_ID or CURMERCE_RELEASE_ADDRESS_IDS" >&2; exit 2; }
[[ "$VERIFY_DATABASE" == true || "$VERIFY_DATABASE" == false ]] || {
  echo "CURMERCE_RELEASE_VERIFY_DATABASE must be true or false" >&2; exit 2;
}
[[ "$VERIFY_REDIS" == true || "$VERIFY_REDIS" == false ]] || {
  echo "CURMERCE_RELEASE_VERIFY_REDIS must be true or false" >&2; exit 2;
}
if [[ "$VERIFY_DATABASE" == true ]]; then
  [[ "$BASE_STOCK" =~ ^[0-9]+$ ]] || {
    echo "set CURMERCE_RELEASE_BASE_STOCK to the pre-drill MySQL stock when database verification is enabled" >&2
    exit 2
  }
  if [[ -n "$MYSQL_DEFAULTS_FILE" ]]; then
    [[ -f "$MYSQL_DEFAULTS_FILE" ]] || { echo "MYSQL_DEFAULTS_FILE does not exist: $MYSQL_DEFAULTS_FILE" >&2; exit 2; }
  elif [[ -z "$MYSQL_PASSWORD" ]]; then
    echo "set MYSQL_DEFAULTS_FILE or MYSQL_PASSWORD/MYSQL_PWD for database verification" >&2
    exit 2
  fi
fi
if [[ "$VERIFY_REDIS" == true && "$VERIFY_DATABASE" != true ]]; then
  echo "Redis convergence verification requires CURMERCE_RELEASE_VERIFY_DATABASE=true" >&2
  exit 2
fi

mysql_args=(--batch --skip-column-names --user="$MYSQL_USER")
if [[ -n "${MYSQL_SOCKET:-}" ]]; then mysql_args+=(--socket="$MYSQL_SOCKET"); else mysql_args+=(--host="$MYSQL_HOST" --port="$MYSQL_PORT"); fi
mysql_query() {
  if [[ -n "$MYSQL_DEFAULTS_FILE" ]]; then
    MYSQL_PWD="$MYSQL_PASSWORD" "$MYSQL_CLI" --defaults-extra-file="$MYSQL_DEFAULTS_FILE" "${mysql_args[@]}" "$MYSQL_DATABASE" -e "$1"
  else
    MYSQL_PWD="$MYSQL_PASSWORD" "$MYSQL_CLI" "${mysql_args[@]}" "$MYSQL_DATABASE" -e "$1"
  fi
}
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

submit() {
  local n="$1" label="$2" base="$INSTANCE_A_URL"
  [[ "$label" == B ]] && base="$INSTANCE_B_URL"
  local -a tokens
  local -a addresses
  IFS=',' read -r -a tokens <<<"$TOKENS_CSV"
  IFS=',' read -r -a addresses <<<"$ADDRESS_IDS_CSV"
  local token="${tokens[$(((n - 1) % ${#tokens[@]}))]}"
  local address="${addresses[$(((n - 1) % ${#addresses[@]}))]}"
  local key="$RUN_ID-$n"
  local body='{"itemId":'"$ITEM_ID"',"addressId":'"$address"',"quantity":1,"idempotencyKey":"'"$key"'"}'
  local ticket
  ticket="$(curl --fail --silent --show-error -H "Authorization: Bearer $token" \
    -H 'Content-Type: application/json' -H 'tenant-id: 1' \
    -X POST "$base/app-api/commerce/release/purchase/async" -d "$body" | jq -r '.data // empty')"
  [[ -n "$ticket" ]] && printf '%s|%s|%s|%s\n' "$ticket" "$label" "$n" "$key"
}
export -f submit
export INSTANCE_A_URL INSTANCE_B_URL TOKENS_CSV ADDRESS_IDS_CSV ITEM_ID RUN_ID

started_ms="$(date +%s%3N)"
mapfile -t accepted_lines < <(
  seq 1 "$ATTEMPTS" | awk '{ print $1, (($1 % 2) == 0 ? "B" : "A") }' \
    | xargs -P "$ATTEMPTS" -n 2 bash -c 'submit "$1" "$2"' _ | awk 'NF'
)
(( ${#accepted_lines[@]} > 0 )) || { echo "no tickets returned" >&2; exit 1; }

accepted_a=0; accepted_b=0
for line in "${accepted_lines[@]}"; do
  IFS='|' read -r _ label _ <<<"$line"
  if [[ "$label" == A ]]; then ((accepted_a += 1)); else ((accepted_b += 1)); fi
done

status_response_for_ticket() {
  local ticket="$1" request_index="$2" response
  local -a tokens
  IFS=',' read -r -a tokens <<<"$TOKENS_CSV"
  local token="${tokens[$(((request_index - 1) % ${#tokens[@]}))]}"
  response="$(curl --fail --silent --show-error -H "Authorization: Bearer $token" \
    -H 'tenant-id: 1' "$INSTANCE_A_URL/app-api/commerce/release/purchase/async/status?ticket=$ticket" 2>/dev/null || true)"
  if [[ -z "$response" ]]; then
    response="$(curl --fail --silent --show-error -H "Authorization: Bearer $token" \
      -H 'tenant-id: 1' "$INSTANCE_B_URL/app-api/commerce/release/purchase/async/status?ticket=$ticket" 2>/dev/null || true)"
  fi
  printf '%s' "$response"
}
status_for_ticket() {
  local response
  response="$(status_response_for_ticket "$1" "$2")"
  jq -r '.data.status // "UNKNOWN"' <<<"$response"
}
export -f status_for_ticket
export -f status_response_for_ticket
export INSTANCE_A_URL INSTANCE_B_URL TOKENS_CSV

deadline=$((SECONDS + POLL_SECONDS))
completed=0; failed=0; pending=0
while (( SECONDS < deadline )); do
  completed=0; failed=0; pending=0
  for line in "${accepted_lines[@]}"; do
    IFS='|' read -r ticket _ request_index <<<"$line"
    status="$(status_for_ticket "$ticket" "$request_index")"
    case "$status" in
      COMPLETED) ((completed += 1)) ;;
      FAILED) ((failed += 1)) ;;
      *) ((pending += 1)) ;;
    esac
  done
  (( pending == 0 )) && break
  sleep 1
done

(( pending == 0 )) || { echo "FAIL: tickets still pending=$pending" >&2; exit 1; }
(( completed <= STOCK )) || { echo "FAIL: completed=$completed exceeds stock=$STOCK" >&2; exit 1; }
if [[ "$EXPECT_EXACT_COMPLETED" == "true" ]]; then
  (( completed == STOCK )) || { echo "FAIL: completed=$completed does not exhaust expected stock=$STOCK" >&2; exit 1; }
fi
if [[ "$VERIFY_DATABASE" == true ]]; then
  completed_orders=0
  completed_purchases=0
  for line in "${accepted_lines[@]}"; do
    IFS='|' read -r ticket _ request_index key <<<"$line"
    response="$(status_response_for_ticket "$ticket" "$request_index")"
    status="$(jq -r '.data.status // "UNKNOWN"' <<<"$response")"
    key_sql="$(safe_sql "$key")"
    order_count="$(mysql_query "SELECT COUNT(*) FROM commerce_order WHERE idempotency_key = '$key_sql' AND deleted = b'0';")"
    if [[ "$status" == COMPLETED ]]; then
      order_id="$(jq -r '.data.result.orderId // empty' <<<"$response")"
      [[ "$order_id" =~ ^[1-9][0-9]*$ ]] || { echo "FAIL: completed ticket $ticket has no order id" >&2; exit 1; }
      purchase_count="$(mysql_query "SELECT COUNT(*) FROM commerce_release_purchase WHERE order_id = $order_id AND item_id = $ITEM_ID AND deleted = b'0';")"
      [[ "$order_count" == 1 ]] || { echo "FAIL: completed key $key has $order_count orders, expected 1" >&2; exit 1; }
      [[ "$purchase_count" == 1 ]] || { echo "FAIL: completed order $order_id has $purchase_count release purchases, expected 1" >&2; exit 1; }
      ((completed_orders += order_count))
      ((completed_purchases += purchase_count))
    else
      [[ "$order_count" == 0 ]] || { echo "FAIL: failed key $key has $order_count orders, expected 0" >&2; exit 1; }
    fi
  done
  [[ "$completed_orders" == "$completed" && "$completed_purchases" == "$completed" ]] || {
    echo "FAIL: completed tickets=$completed but orders=$completed_orders release_purchases=$completed_purchases" >&2
    exit 1
  }
  campaign_id="$(mysql_query "SELECT campaign_id FROM commerce_release_item WHERE id = $ITEM_ID AND deleted = b'0';")"
  database_stock="$(mysql_query "SELECT stock FROM commerce_release_item WHERE id = $ITEM_ID AND deleted = b'0';")"
  [[ "$campaign_id" =~ ^[1-9][0-9]*$ && "$database_stock" =~ ^[0-9]+$ ]] || {
    echo "FAIL: release item $ITEM_ID is unavailable for database convergence verification" >&2; exit 1;
  }
  expected_database_stock=$((BASE_STOCK - completed))
  (( expected_database_stock >= 0 )) || { echo "FAIL: completed tickets exceed supplied base stock" >&2; exit 1; }
  [[ "$database_stock" == "$expected_database_stock" ]] || {
    echo "FAIL: MySQL stock=$database_stock, expected base_stock=$BASE_STOCK minus completed=$completed" >&2; exit 1;
  }
  if [[ "$VERIFY_REDIS" == true ]]; then
    redis_stock="$(redis GET "curmerce:release:v1:stock:$campaign_id:$ITEM_ID" || true)"
    redis_reserved="$(redis GET "curmerce:release:v1:reserved:$campaign_id:$ITEM_ID" || true)"
    [[ "$redis_stock" == "$database_stock" ]] || {
      echo "FAIL: Redis stock=$redis_stock does not match MySQL stock=$database_stock" >&2; exit 1;
    }
    [[ -z "$redis_reserved" || "$redis_reserved" == 0 ]] || {
      echo "FAIL: Redis reserved=$redis_reserved after terminal tickets" >&2; exit 1;
    }
  fi
fi

finished_ms="$(date +%s%3N)"
elapsed_ms=$((finished_ms - started_ms)); (( elapsed_ms > 0 )) || elapsed_ms=1
throughput="$(awk -v count="${#accepted_lines[@]}" -v elapsed="$elapsed_ms" 'BEGIN { printf "%.2f", count / (elapsed / 1000) }')"
printf 'PASS: limited-release multi-instance; instance_a=%s instance_b=%s accepted=%s completed=%s failed=%s stock=%s database_verified=%s redis_verified=%s elapsed_ms=%s throughput_req_per_sec=%s\n' \
  "$accepted_a" "$accepted_b" "${#accepted_lines[@]}" "$completed" "$failed" "$STOCK" "$VERIFY_DATABASE" "$VERIFY_REDIS" "$elapsed_ms" "$throughput"

#!/usr/bin/env bash
set -Eeuo pipefail

# End-to-end Redis Stream purchase check. It deliberately uses the public
# async contract and disposable test data; it does not print authorization.
BASE_URL="${CURMERCE_CORE_URL:-http://127.0.0.1:48080}"
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

(( STOCK >= 0 && ATTEMPTS > 0 && POLL_SECONDS > 0 )) || { echo "invalid smoke parameters" >&2; exit 2; }
[[ -n "$TOKENS_CSV" ]] || { echo "set CURMERCE_TEST_TOKEN or CURMERCE_TEST_TOKENS" >&2; exit 2; }
[[ -n "$ADDRESS_IDS_CSV" ]] || { echo "set CURMERCE_RELEASE_ADDRESS_ID or CURMERCE_RELEASE_ADDRESS_IDS" >&2; exit 2; }
started_ms="$(date +%s%3N)"

submit() {
  local n="$1"
  local -a tokens
  local -a addresses
  IFS=',' read -r -a tokens <<<"$TOKENS_CSV"
  IFS=',' read -r -a addresses <<<"$ADDRESS_IDS_CSV"
  local token="${tokens[$(((n - 1) % ${#tokens[@]}))]}"
  local address="${addresses[$(((n - 1) % ${#addresses[@]}))]}"
  local body='{"itemId":'"$ITEM_ID"',"addressId":'"$address"',"quantity":1,"idempotencyKey":"e2e-'"$n"'"}'
  local ticket
  ticket="$(curl --fail --silent --show-error -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -H 'tenant-id: 1' \
    -X POST "$BASE_URL/app-api/commerce/release/purchase/async" -d "$body" \
    | jq -r '.data // empty')"
  [[ -n "$ticket" ]] && printf '%s|%s\n' "$ticket" "$n"
}
export -f submit
export BASE_URL TOKENS_CSV ADDRESS_IDS_CSV ITEM_ID STOCK POLL_SECONDS

mapfile -t tickets < <(seq 1 "$ATTEMPTS" | xargs -P "$ATTEMPTS" -n 1 bash -c 'submit "$1"' _ | awk 'NF')
(( ${#tickets[@]} > 0 )) || { echo "no tickets returned" >&2; exit 1; }

deadline=$((SECONDS + POLL_SECONDS))
completed=0
failed=0
while (( SECONDS < deadline )); do
  completed=0; failed=0; pending=0
  for line in "${tickets[@]}"; do
    ticket="${line%%|*}"
    request_index="${line##*|}"
    IFS=',' read -r -a tokens <<<"$TOKENS_CSV"
    token="${tokens[$(((request_index - 1) % ${#tokens[@]}))]}"
    response="$(curl --fail --silent --show-error -H "Authorization: Bearer $token" -H 'tenant-id: 1' \
      "$BASE_URL/app-api/commerce/release/purchase/async/status?ticket=$ticket")"
    status="$(jq -r '.data.status // "UNKNOWN"' <<<"$response")"
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
finished_ms="$(date +%s%3N)"
elapsed_ms=$((finished_ms - started_ms))
(( elapsed_ms > 0 )) || elapsed_ms=1
throughput="$(awk -v count="${#tickets[@]}" -v elapsed="$elapsed_ms" 'BEGIN { printf "%.2f", count / (elapsed / 1000) }')"
printf 'PASS: limited-release e2e; accepted=%s completed=%s failed=%s stock=%s elapsed_ms=%s throughput_req_per_sec=%s\n' \
  "${#tickets[@]}" "$completed" "$failed" "$STOCK" "$elapsed_ms" "$throughput"

#!/usr/bin/env bash
set -Eeuo pipefail

# Verify Search as an independently deployable projection service. This script
# never prints the optional rebuild token or any other private configuration.
SEARCH_URL="${CURMERCE_SEARCH_BASE_URL:-http://127.0.0.1:48085}"
GATEWAY_URL="${CURMERCE_GATEWAY_BASE_URL:-http://127.0.0.1:48082}"
ELASTICSEARCH_URL="${CURMERCE_ELASTICSEARCH_URL:-http://127.0.0.1:19200}"
KAFKA_HOST="${CURMERCE_KAFKA_HOST:-127.0.0.1}"
KAFKA_PORT="${CURMERCE_KAFKA_PORT:-19092}"
REBUILD_TOKEN="${CURMERCE_SEARCH_REBUILD_TOKEN:-}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

http_code() {
  curl --silent --show-error --output /dev/null --write-out '%{http_code}' --max-time 30 "$@"
}

assert_code() {
  local expected="$1"
  shift
  local actual
  actual="$(http_code "$@")"
  [[ "$actual" == "$expected" ]] || fail "expected HTTP $expected, got $actual for $*"
}

assert_code 200 "$SEARCH_URL/actuator/health"
assert_code 200 "$SEARCH_URL/actuator/prometheus"
curl --silent --show-error --fail --max-time 10 "$ELASTICSEARCH_URL/_cluster/health" >"$TMP_DIR/es-health.json" \
  || fail "Elasticsearch cluster health is unavailable"
grep -Eq '"status"[[:space:]]*:[[:space:]]*"(green|yellow)"' "$TMP_DIR/es-health.json" \
  || fail "Elasticsearch cluster is not green or yellow"
timeout 5 bash -c "</dev/tcp/${KAFKA_HOST}/${KAFKA_PORT}" >/dev/null 2>&1 \
  || fail "Kafka broker is unavailable at ${KAFKA_HOST}:${KAFKA_PORT}"

assert_code 200 "$GATEWAY_URL/app-api/search/products?pageNo=1&pageSize=2"
assert_code 200 "$GATEWAY_URL/app-api/search/posts?pageNo=1&pageSize=2"

rebuild_args=(-X POST)
if [[ -n "$REBUILD_TOKEN" ]]; then
  rebuild_args+=(-H "X-Curmerce-Search-Token: $REBUILD_TOKEN")
fi
curl --silent --show-error --fail --max-time 60 "${rebuild_args[@]}" \
  "$GATEWAY_URL/app-api/search/rebuild/all" >"$TMP_DIR/rebuild.json" \
  || fail "Search projection rebuild failed"
grep -Eq '"completed"[[:space:]]*:[[:space:]]*true' "$TMP_DIR/rebuild.json" \
  || fail "Search rebuild did not report completed=true"

assert_code 200 "$GATEWAY_URL/app-api/search/products?keyword=runtime&pageNo=1&pageSize=2"
assert_code 200 "$GATEWAY_URL/app-api/search/posts?keyword=runtime&pageNo=1&pageSize=2"

printf 'PASS: Search projection smoke completed (Kafka, Elasticsearch, rebuild, and routed queries)\n'

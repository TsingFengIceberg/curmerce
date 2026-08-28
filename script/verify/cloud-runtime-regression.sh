#!/usr/bin/env bash
set -Eeuo pipefail

# Host-side regression for the four-process local topology. It intentionally
# uses only loopback endpoints and never prints credential values.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GATEWAY_BASE_URL="${CURMERCE_GATEWAY_BASE_URL:-http://127.0.0.1:48082}"
CORE_URL="${CURMERCE_CORE_BASE_URL:-http://127.0.0.1:48080}"
COMMUNITY_URL="${CURMERCE_COMMUNITY_BASE_URL:-http://127.0.0.1:48083}"
AGENT_URL="${CURMERCE_AGENT_BASE_URL:-http://127.0.0.1:48084}"
SEARCH_URL="${CURMERCE_SEARCH_BASE_URL:-http://127.0.0.1:48085}"
ELASTICSEARCH_URL="${CURMERCE_ELASTICSEARCH_URL:-http://127.0.0.1:19200}"
KAFKA_HOST="${CURMERCE_KAFKA_HOST:-127.0.0.1}"
KAFKA_PORT="${CURMERCE_KAFKA_PORT:-19092}"
SEARCH_RUNTIME_REQUIRED="${CURMERCE_SEARCH_RUNTIME_REQUIRED:-false}"
COMMUNITY_UNIT="${CURMERCE_COMMUNITY_UNIT:-curmerce-community.service}"
MYSQL_CLIENT="${MYSQL_CLIENT:-mysql}"
TMP_DIR="$(mktemp -d)"
COMMUNITY_STOPPED=0

cleanup() {
  if [[ "$COMMUNITY_STOPPED" == 1 ]]; then
    systemctl --user start "$COMMUNITY_UNIT" >/dev/null 2>&1 || true
  fi
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

http_code() {
  curl --silent --show-error --output /dev/null --write-out '%{http_code}' --max-time 10 "$@"
}

assert_code() {
  local expected="$1"
  shift
  local actual
  actual="$(http_code "$@")"
  [[ "$actual" == "$expected" ]] || fail "expected HTTP $expected, got $actual for $*"
}

assert_up() {
  local url="$1"
  assert_code 200 "$url/actuator/health"
}

assert_up "$CORE_URL"
assert_up "$COMMUNITY_URL"
assert_up "$AGENT_URL"
assert_up "$GATEWAY_BASE_URL"
if [[ "$SEARCH_RUNTIME_REQUIRED" == "true" ]]; then
  assert_up "$SEARCH_URL"
fi

for endpoint in "$CORE_URL" "$COMMUNITY_URL" "$AGENT_URL" "$GATEWAY_BASE_URL"; do
  assert_code 200 "$endpoint/actuator/prometheus"
done
if [[ "$SEARCH_RUNTIME_REQUIRED" == "true" ]]; then
  assert_code 200 "$SEARCH_URL/actuator/prometheus"
  curl --silent --show-error --fail --max-time 10 "$ELASTICSEARCH_URL/_cluster/health" >"$TMP_DIR/elasticsearch-health.json" \
    || fail "Elasticsearch cluster health is unavailable"
  grep -Eq '"status"[[:space:]]*:[[:space:]]*"(green|yellow)"' "$TMP_DIR/elasticsearch-health.json" \
    || fail "Elasticsearch cluster is not green or yellow"
  if ! timeout 5 bash -c "</dev/tcp/${KAFKA_HOST}/${KAFKA_PORT}" >/dev/null 2>&1; then
    fail "Kafka broker is unavailable at ${KAFKA_HOST}:${KAFKA_PORT}"
  fi
fi

curl --silent --show-error --fail --max-time 10 \
  "$COMMUNITY_URL/actuator/prometheus" >"$TMP_DIR/community-metrics"
grep -Eq '^curmerce_community_media_outbox_unfinished[[:space:]]+[0-9]+(\.0)?$' \
  "$TMP_DIR/community-metrics" || fail "Community Outbox unfinished gauge is missing"

assert_code 200 "$GATEWAY_BASE_URL/app-api/commerce/catalog/product-page?pageNo=1&pageSize=2"
assert_code 200 "$GATEWAY_BASE_URL/app-api/community/post/page?pageNo=1&pageSize=2"
if [[ "$SEARCH_RUNTIME_REQUIRED" == "true" ]]; then
  assert_code 200 "$SEARCH_URL/app-api/search/products?pageNo=1&pageSize=2"
  assert_code 200 "$SEARCH_URL/app-api/search/posts?pageNo=1&pageSize=2"
  assert_code 200 "$GATEWAY_BASE_URL/app-api/search/products?pageNo=1&pageSize=2"
  assert_code 200 "$GATEWAY_BASE_URL/app-api/search/posts?pageNo=1&pageSize=2"
fi

systemctl --user stop "$COMMUNITY_UNIT"
COMMUNITY_STOPPED=1

for attempt in {1..12}; do
  if [[ "$(http_code "$GATEWAY_BASE_URL/app-api/community/post/page?pageNo=1&pageSize=2")" == 503 ]]; then
    break
  fi
  [[ "$attempt" == 12 ]] && fail "Community route did not expose HTTP 503 after stopping the service"
  sleep 1
done

assert_code 200 "$GATEWAY_BASE_URL/app-api/commerce/catalog/product-page?pageNo=1&pageSize=2"
curl --silent --show-error --fail --max-time 10 \
  -H 'Content-Type: application/json' \
  -d '{"query":"runtime regression"}' \
  "$GATEWAY_BASE_URL/app-api/agent/assist" >"$TMP_DIR/agent-degraded.json"
grep -q 'community' "$TMP_DIR/agent-degraded.json" \
  || fail "Agent response did not report Community degradation"

systemctl --user start "$COMMUNITY_UNIT"
COMMUNITY_STOPPED=0
for attempt in {1..30}; do
  if [[ "$(http_code "$COMMUNITY_URL/actuator/health")" == 200 ]] \
      && [[ "$(http_code "$GATEWAY_BASE_URL/app-api/community/post/page?pageNo=1&pageSize=2")" == 200 ]]; then
    break
  fi
  [[ "$attempt" == 30 ]] && fail "Community did not recover through Nacos/Gateway"
  sleep 2
done

# Verify the schema boundary when local credentials are available. The failed
# SELECTs are expected and are intentionally suppressed from the report.
if [[ -n "${MYSQL_PASSWORD:-}" && -n "${MYSQL_HOST:-}" && -n "${MYSQL_PORT:-}" ]]; then
  export MYSQL_PWD="$MYSQL_PASSWORD"
  if "$MYSQL_CLIENT" --protocol=tcp -h "$MYSQL_HOST" -P "$MYSQL_PORT" \
      -u curmerce_community -N -e 'SELECT 1 FROM curmerce.system_users LIMIT 1' \
      >/dev/null 2>&1; then
    fail 'Community account can read the Core schema'
  fi
  if ! "$MYSQL_CLIENT" --protocol=tcp -h "$MYSQL_HOST" -P "$MYSQL_PORT" \
      -u "$MYSQL_USER" -N -e 'SELECT 1 FROM curmerce.commerce_kafka_consumer_receipt LIMIT 1' \
      >/dev/null 2>&1; then
    fail 'Core Kafka consumer receipt migration is missing'
  fi
  if "$MYSQL_CLIENT" --protocol=tcp -h "$MYSQL_HOST" -P "$MYSQL_PORT" \
      -u "$MYSQL_USER" -N -e 'SELECT 1 FROM curmerce_community.community_post LIMIT 1' \
      >/dev/null 2>&1; then
    fail 'Core account can read the Community schema'
  fi
  COMMUNITY_DB_PASSWORD="${COMMUNITY_MYSQL_PASSWORD:-$MYSQL_PASSWORD}"
  export MYSQL_PWD="$COMMUNITY_DB_PASSWORD"
  if ! "$MYSQL_CLIENT" --protocol=tcp -h "$MYSQL_HOST" -P "$MYSQL_PORT" \
      -u curmerce_community -N -e 'SELECT 1 FROM curmerce_community.community_search_outbox LIMIT 1' \
      >/dev/null 2>&1; then
    fail 'Community search Outbox migration is missing'
  fi
  unset MYSQL_PWD
fi

printf 'PASS: Curmerce cloud runtime regression completed\n'

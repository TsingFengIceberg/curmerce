#!/usr/bin/env bash
set -Eeuo pipefail

GATEWAY_BASE_URL="${CURMERCE_GATEWAY_BASE_URL:-http://127.0.0.1:48082}"
CORE_URL="${CURMERCE_CORE_BASE_URL:-http://127.0.0.1:48080}"
COMMUNITY_URL="${CURMERCE_COMMUNITY_BASE_URL:-http://127.0.0.1:48083}"
AGENT_URL="${CURMERCE_AGENT_BASE_URL:-http://127.0.0.1:48084}"
COMMUNITY_UNIT="${CURMERCE_COMMUNITY_UNIT:-curmerce-community.service}"
TMP_DIR="$(mktemp -d)"
COMMUNITY_STOPPED=0

cleanup() {
  if [[ "$COMMUNITY_STOPPED" == 1 ]]; then
    systemctl --user start "$COMMUNITY_UNIT" >/dev/null 2>&1 || true
  fi
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

code() {
  curl --silent --show-error --output /dev/null --write-out '%{http_code}' --max-time 10 "$@"
}

assert_code() {
  local expected="$1"
  shift
  local actual
  actual="$(code "$@")"
  [[ "$actual" == "$expected" ]] || { printf 'FAIL: expected HTTP %s, got %s for %s\n' "$expected" "$actual" "$*" >&2; exit 1; }
}

wait_for_code() {
  local expected="$1"
  local url="$2"
  local attempts="${3:-30}"
  local actual
  for attempt in $(seq 1 "$attempts"); do
    actual="$(code "$url" 2>/dev/null)"
    [[ "$actual" == "$expected" ]] && return 0
    [[ "$attempt" == "$attempts" ]] && break
    sleep 2
  done
  printf 'FAIL: expected HTTP %s after waiting, got %s for %s\n' "$expected" "$actual" "$url" >&2
  exit 1
}

# Services may be in systemd's restart window after a previous local run.
wait_for_code 200 "$CORE_URL/actuator/health"
wait_for_code 200 "$COMMUNITY_URL/actuator/health"
wait_for_code 200 "$AGENT_URL/actuator/health"
wait_for_code 200 "$GATEWAY_BASE_URL/actuator/health"
assert_code 200 "$GATEWAY_BASE_URL/app-api/commerce/catalog/product-page?pageNo=1&pageSize=2"
assert_code 200 "$GATEWAY_BASE_URL/app-api/community/post/page?pageNo=1&pageSize=2"

systemctl --user stop "$COMMUNITY_UNIT"
COMMUNITY_STOPPED=1
for attempt in {1..12}; do
  [[ "$(code "$GATEWAY_BASE_URL/app-api/community/post/page?pageNo=1&pageSize=2")" == 503 ]] && break
  [[ "$attempt" == 12 ]] && { printf 'FAIL: Community route did not become HTTP 503\n' >&2; exit 1; }
  sleep 1
done

assert_code 200 "$GATEWAY_BASE_URL/app-api/commerce/catalog/product-page?pageNo=1&pageSize=2"
curl --silent --show-error --fail --max-time 10 -H 'Content-Type: application/json' \
  -d '{"query":"service boundary smoke"}' "$GATEWAY_BASE_URL/app-api/agent/assist" >"$TMP_DIR/agent.json"
grep -q 'community' "$TMP_DIR/agent.json" || { printf 'FAIL: Agent response did not report Community degradation\n' >&2; exit 1; }

systemctl --user start "$COMMUNITY_UNIT"
COMMUNITY_STOPPED=0
for attempt in {1..30}; do
  if [[ "$(code "$COMMUNITY_URL/actuator/health")" == 200 ]] \
      && [[ "$(code "$GATEWAY_BASE_URL/app-api/community/post/page?pageNo=1&pageSize=2")" == 200 ]]; then
    printf 'PASS: Agent/Community service boundary smoke completed\n'
    exit 0
  fi
  [[ "$attempt" == 30 ]] && break
  sleep 2
done
printf 'FAIL: Community did not recover through Nacos/Gateway\n' >&2
exit 1

#!/usr/bin/env bash
set -euo pipefail

# Verifies the Redis primitives used by the distributed limited-release queue.
# The application must be stopped or pointed at a disposable Redis database
# before running this check; no production keys are touched.
REDIS_HOST="${CURMERCE_REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${CURMERCE_REDIS_PORT:-16379}"
REDIS_DB="${CURMERCE_REDIS_DB:-15}"
REDIS_USER="${CURMERCE_REDIS_USER:-${REDIS_USER:-}}"
REDIS_PASSWORD="${CURMERCE_REDIS_PASSWORD:-${REDIS_PASSWORD:-}}"
REDIS_CLI=(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" -n "$REDIS_DB" --raw)
[[ -n "$REDIS_USER" ]] && REDIS_CLI+=(--user "$REDIS_USER")

redis() {
  if [[ -n "$REDIS_PASSWORD" ]]; then
    REDISCLI_AUTH="$REDIS_PASSWORD" "${REDIS_CLI[@]}" "$@"
  else
    "${REDIS_CLI[@]}" "$@"
  fi
}
PREFIX="curmerce:verify:release:$RANDOM"
STREAM="$PREFIX:stream"
GROUP="$PREFIX:group"
TICKET="$PREFIX:ticket"
RETRY="$PREFIX:retry"
LOCK="$PREFIX:lock"

cleanup() { redis DEL "$STREAM" "$TICKET" "$RETRY" "$LOCK" >/dev/null || true; }
trap cleanup EXIT

redis PING | grep -qx PONG

# The ticket and stream entry are created as one Redis transaction/script unit.
redis HSET "$TICKET" owner 7 status QUEUED request '{"itemId":1}' >/dev/null
redis XADD "$STREAM" '*' ticket "$TICKET" userId 7 request '{"itemId":1}' >/dev/null
redis XGROUP CREATE "$STREAM" "$GROUP" 0-0 MKSTREAM >/dev/null

ID=$(redis XREADGROUP GROUP "$GROUP" consumer-1 COUNT 1 STREAMS "$STREAM" '>' | sed -n '1p')
test -n "$ID"
ENTRY_ID=$(redis XRANGE "$STREAM" - + COUNT 1 | sed -n '1p')
test -n "$ENTRY_ID"
PENDING=$(redis XPENDING "$STREAM" "$GROUP" | sed -n '1p' | awk '{print $1}')
test "${PENDING:-0}" -eq 1

redis XCLAIM "$STREAM" "$GROUP" consumer-2 0 "$ENTRY_ID" >/dev/null
PENDING_AFTER_CLAIM=$(redis XPENDING "$STREAM" "$GROUP" | sed -n '1p' | awk '{print $1}')
test "${PENDING_AFTER_CLAIM:-0}" -eq 1
redis XACK "$STREAM" "$GROUP" "$ENTRY_ID" >/dev/null
PENDING_AFTER_ACK=$(redis XPENDING "$STREAM" "$GROUP" | sed -n '1p' | awk '{print $1}')
test "${PENDING_AFTER_ACK:-0}" -eq 0

NOW=$(date +%s%3N)
redis ZADD "$RETRY" "$NOW" "$TICKET" >/dev/null
test "$(redis ZCARD "$RETRY")" -eq 1
redis ZREM "$RETRY" "$TICKET" >/dev/null
test "$(redis ZCARD "$RETRY")" -eq 0

echo "limited-release distributed queue smoke: PASS"

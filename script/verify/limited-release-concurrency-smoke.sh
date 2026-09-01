#!/usr/bin/env bash
set -Eeuo pipefail

# Redis/Lua-only gate for the limited-release reservation experiment. It does
# not create orders and must be run against disposable keys or a local Redis.
REDIS_CLI="${REDIS_CLI:-redis-cli}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_DB="${REDIS_DB:-0}"
STOCK="${CURMERCE_RELEASE_TEST_STOCK:-20}"
USERS="${CURMERCE_RELEASE_TEST_USERS:-100}"
USER_LIMIT="${CURMERCE_RELEASE_TEST_USER_LIMIT:-1}"
QUANTITY="${CURMERCE_RELEASE_TEST_QUANTITY:-1}"
PREFIX="curmerce:release:smoke:$$"
STOCK_KEY="$PREFIX:stock"
RESERVED_KEY="$PREFIX:reserved"

(( STOCK > 0 && USERS > 0 && USER_LIMIT > 0 && QUANTITY > 0 )) || {
  printf 'FAIL: stock, users, user limit, and quantity must be positive\n' >&2
  exit 1
}

redis() {
  "$REDIS_CLI" --raw -h "$REDIS_HOST" -p "$REDIS_PORT" -n "$REDIS_DB" "$@"
}

cleanup() {
  redis DEL "$STOCK_KEY" "$RESERVED_KEY" >/dev/null 2>&1 || true
  for user in $(seq 1 "$USERS"); do
    redis DEL "$PREFIX:user:$user" >/dev/null 2>&1 || true
  done
}
trap cleanup EXIT

redis SET "$STOCK_KEY" "$STOCK" >/dev/null
redis SET "$RESERVED_KEY" 0 >/dev/null

reserve_script=$(cat <<'LUA'
local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
if stock < 0 then return -3 end
local quantity = tonumber(ARGV[1])
local limit = tonumber(ARGV[2])
local user = tonumber(redis.call('GET', KEYS[3]) or '0')
if user + quantity > limit then return -2 end
if stock < quantity then return -1 end
redis.call('DECRBY', KEYS[1], quantity)
redis.call('INCRBY', KEYS[2], quantity)
redis.call('INCRBY', KEYS[3], quantity)
return stock - quantity
LUA
)

export -f redis
export REDIS_CLI REDIS_HOST REDIS_PORT REDIS_DB PREFIX STOCK_KEY RESERVED_KEY
export RESERVE_SCRIPT="$reserve_script" USER_LIMIT QUANTITY

reserve_one() {
  local user="$1"
  redis EVAL "$RESERVE_SCRIPT" 3 "$STOCK_KEY" "$RESERVED_KEY" "$PREFIX:user:$user" "$QUANTITY" "$USER_LIMIT"
}
export -f reserve_one

results="$(seq 1 "$USERS" | xargs -P "$USERS" -n 1 bash -c 'reserve_one "$1"' _ | tr -d '\r')"
successes="$(awk '$1 >= 0 { count++ } END { print count + 0 }' <<<"$results")"
remaining="$(redis GET "$STOCK_KEY")"
reserved="$(redis GET "$RESERVED_KEY")"

[[ "$successes" -le "$STOCK" ]] || { printf 'FAIL: oversold, successes=%s stock=%s\n' "$successes" "$STOCK" >&2; exit 1; }
[[ "$remaining" -ge 0 ]] || { printf 'FAIL: Redis stock became negative: %s\n' "$remaining" >&2; exit 1; }
[[ "$reserved" -eq "$successes" ]] || {
  printf 'FAIL: reserved counter=%s does not equal successful reservations=%s\n' "$reserved" "$successes" >&2
  exit 1
}

printf 'PASS: limited-release Lua gate; attempts=%s successes=%s remaining=%s reserved=%s\n' \
  "$USERS" "$successes" "$remaining" "$reserved"

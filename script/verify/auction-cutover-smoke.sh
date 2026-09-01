#!/usr/bin/env bash
set -Eeuo pipefail

# Verifies the migration snapshot and live Auction-owned row counts.  This is
# intentionally read-only; it never enables the feature flag or changes data.
MYSQL_CLI="${MYSQL_CLI:-mysql}"
MYSQL_SOCKET="${MYSQL_SOCKET:-}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-13306}"
MYSQL_DATABASE="${MYSQL_DATABASE:-curmerce}"
MYSQL_USER="${MYSQL_USER:-curmerce}"
MYSQL_DEFAULTS_FILE="${MYSQL_DEFAULTS_FILE:-}"

mysql_args=(--batch --skip-column-names)
if [[ -n "$MYSQL_DEFAULTS_FILE" ]]; then mysql_args+=(--defaults-extra-file="$MYSQL_DEFAULTS_FILE"); fi
if [[ -n "$MYSQL_SOCKET" ]]; then mysql_args+=(--socket="$MYSQL_SOCKET"); else mysql_args+=(--host="$MYSQL_HOST" --port="$MYSQL_PORT"); fi
mysql_args+=(--user="$MYSQL_USER")

query() { "$MYSQL_CLI" "${mysql_args[@]}" "$@"; }
fail() { printf 'FAIL: %s\n' "$1" >&2; exit 1; }

snapshot="$(query "$MYSQL_DATABASE" -e "SELECT source_session_count,target_session_count,source_bid_count,target_bid_count,verified FROM curmerce_auction.ownership_cutover ORDER BY id DESC LIMIT 1;")" \
  || fail "ownership_cutover is unavailable; apply migration 28 first"
[[ -n "$snapshot" ]] || fail "ownership_cutover has no snapshot"
read -r source_sessions snapshot_sessions source_bids snapshot_bids verified <<<"$snapshot"
[[ "$verified" == "1" ]] || fail "migration snapshot is not verified"
live_sessions="$(query curmerce_auction -e "SELECT COUNT(*) FROM auction_session WHERE deleted=0;")"
live_bids="$(query curmerce_auction -e "SELECT COUNT(*) FROM auction_bid WHERE deleted=0;")"
[[ "$live_sessions" == "$source_sessions" && "$live_sessions" == "$snapshot_sessions" ]] \
  || fail "Auction session count mismatch: source=$source_sessions snapshot=$snapshot_sessions live=$live_sessions"
[[ "$live_bids" == "$source_bids" && "$live_bids" == "$snapshot_bids" ]] \
  || fail "Auction bid count mismatch: source=$source_bids snapshot=$snapshot_bids live=$live_bids"
printf 'PASS: Auction ownership cutover verified; sessions=%s bids=%s\n' "$live_sessions" "$live_bids"

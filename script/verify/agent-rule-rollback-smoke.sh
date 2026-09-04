#!/usr/bin/env bash
set -Eeuo pipefail

# Opt-in smoke for the versioned JDBC rule store. It requires an operator to
# acknowledge a disposable rule database because it writes a temporary
# snapshot, then restores the original snapshot as a later monotonic version.
BASE_URL="${CURMERCE_AGENT_URL:-http://127.0.0.1:48084}"
TOKEN="${CURMERCE_INTERNAL_TOKEN:?set CURMERCE_INTERNAL_TOKEN}"
BASE_URL="${BASE_URL%/}"

curl_json() {
  curl --fail --silent --show-error --max-time "${CURMERCE_AGENT_CURL_TIMEOUT_SECONDS:-15}" \
    -H "X-Curmerce-Internal-Token: $TOKEN" -H 'Content-Type: application/json' "$@"
}

current="$(curl_json "$BASE_URL/internal-api/agent/rules")"
version="$(jq -r '.data.version // 0' <<<"$current")"
rules="$(jq -c '.data.rules // {}' <<<"$current")"
[[ "$version" =~ ^[0-9]+$ && "$version" -gt 0 ]] || { echo "rule JDBC store has no current version" >&2; exit 1; }
temporary="$(jq -c '. + {"smokeMarker":"agent-rule-rollback"}' <<<"$rules")"
updated="$(curl_json -X PUT "$BASE_URL/internal-api/agent/rules" -d "$(jq -nc --argjson version "$version" --argjson rules "$temporary" '{version:($version + 1),rules:$rules}')")"
new_version="$(jq -r '.data.version // 0' <<<"$updated")"
[[ "$new_version" =~ ^[0-9]+$ && "$new_version" -gt "$version" ]] || { echo "rule update did not advance version" >&2; exit 1; }
rolled="$(curl_json -X POST "$BASE_URL/internal-api/agent/rules/rollback" -d "$(jq -nc --argjson target "$version" --argjson current "$new_version" '{targetVersion:$target,expectedCurrentVersion:$current}')")"
rolled_version="$(jq -r '.data.version // 0' <<<"$rolled")"
[[ "$rolled_version" =~ ^[0-9]+$ && "$rolled_version" -gt "$new_version" ]] || { echo "rule rollback did not create a new monotonic version" >&2; exit 1; }
printf '%s' "$rolled" | jq -e '(.data.rules.smokeMarker // null) == null' >/dev/null || {
  echo "rule rollback did not restore the original snapshot" >&2; exit 1;
}
echo "PASS: Agent JDBC rule compare-and-set and rollback smoke"

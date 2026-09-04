#!/usr/bin/env bash
set -Eeuo pipefail

# Full Agent integration smoke using unique document markers. It never prints
# internal tokens, provider responses, or persisted document contents.
BASE_URL="${CURMERCE_AGENT_URL:-http://127.0.0.1:48084}"
TOKEN="${CURMERCE_INTERNAL_TOKEN:?set CURMERCE_INTERNAL_TOKEN}"
SUFFIX="$(date +%s)-$$"
DOCUMENT_ID="agent-smoke-$SUFFIX"
SOURCE="agent-smoke-$SUFFIX"
OLD_MARKER="curmerce-old-$SUFFIX"
NEW_MARKER="curmerce-new-$SUFFIX"
POLL_SECONDS="${CURMERCE_AGENT_REINDEX_POLL_SECONDS:-20}"
REQUIRE_EXTERNAL_PROJECTION="${CURMERCE_AGENT_REQUIRE_EXTERNAL_PROJECTION:-false}"

[[ "$REQUIRE_EXTERNAL_PROJECTION" == true || "$REQUIRE_EXTERNAL_PROJECTION" == false ]] || {
  echo "CURMERCE_AGENT_REQUIRE_EXTERNAL_PROJECTION must be true or false" >&2; exit 2;
}

curl_json() {
  curl --fail --silent --show-error --max-time "${CURMERCE_AGENT_CURL_TIMEOUT_SECONDS:-15}" \
    -H "X-Curmerce-Internal-Token: $TOKEN" -H 'Content-Type: application/json' "$@"
}

search_contains() {
  local marker="$1" result encoded
  encoded="$(jq -nr --arg value "$marker" '$value | @uri')"
  result="$(curl_json "$BASE_URL/app-api/agent/knowledge/search?query=$encoded&limit=20&source=$SOURCE")"
  printf '%s' "$result" | jq -e --arg marker "$marker" '[.data[]?.text | contains($marker)] | any' >/dev/null
}

search_excludes() {
  local marker="$1" result encoded
  encoded="$(jq -nr --arg value "$marker" '$value | @uri')"
  result="$(curl_json "$BASE_URL/app-api/agent/knowledge/search?query=$encoded&limit=20&source=$SOURCE")"
  printf '%s' "$result" | jq -e --arg marker "$marker" '[.data[]?.text | contains($marker)] | any | not' >/dev/null
}

filler="$(printf '%*s' 2200 '')"
filler="${filler// /x}"
long_body="$(jq -nc --arg id "$DOCUMENT_ID" --arg source "$SOURCE" --arg text "$OLD_MARKER $filler" '{id:$id, source:$source, text:$text, metadata:{kind:"smoke"}}')"
curl_json -X POST "$BASE_URL/app-api/agent/knowledge/documents" -d "$long_body" | jq -e '.code == 0 and .data == true' >/dev/null
search_contains "$OLD_MARKER"

short_body="$(jq -nc --arg id "$DOCUMENT_ID" --arg source "$SOURCE" --arg text "$NEW_MARKER replacement" '{id:$id, source:$source, text:$text, metadata:{kind:"smoke"}}')"
curl_json -X POST "$BASE_URL/app-api/agent/knowledge/documents" -d "$short_body" | jq -e '.code == 0 and .data == true' >/dev/null
search_excludes "$OLD_MARKER"
search_contains "$NEW_MARKER"

curl_json -X DELETE "$BASE_URL/app-api/agent/knowledge/documents/$DOCUMENT_ID" | jq -e '.code == 0 and .data == true' >/dev/null
search_excludes "$NEW_MARKER"
curl_json -X POST "$BASE_URL/app-api/agent/knowledge/reconcile" -d '{}' | jq -e '.code == 0' >/dev/null

async_body="$(jq -nc --arg source "$SOURCE" --arg id "$DOCUMENT_ID" --arg text "async-$NEW_MARKER" '{source:$source, documents:[{id:$id, source:$source, text:$text, metadata:{kind:"smoke"}}]}')"
job="$(curl_json -X POST "$BASE_URL/app-api/agent/knowledge/reindex/async" -d "$async_body" | jq -er '.data.id')"
deadline=$((SECONDS + POLL_SECONDS))
while (( SECONDS < deadline )); do
  status="$(curl_json "$BASE_URL/app-api/agent/knowledge/reindex/async/status?jobId=$job")"
  state="$(jq -r '.data.status // "UNKNOWN"' <<<"$status")"
  [[ "$state" == "COMPLETED" ]] && break
  [[ "$state" == "FAILED" ]] && { echo "Agent async reindex failed" >&2; exit 1; }
  sleep 1
done
[[ "${state:-UNKNOWN}" == "COMPLETED" ]] || { echo "Agent async reindex did not complete" >&2; exit 1; }
search_contains "async-$NEW_MARKER"

if [[ "$REQUIRE_EXTERNAL_PROJECTION" == true ]]; then
  projection_status="$(curl_json "$BASE_URL/app-api/agent/knowledge/status")"
  printf '%s' "$projection_status" | jq -e '
    .code == 0
    and .data.backend != "redis-vector-adapter"
    and .data.backend != "local-vector-adapter"
    and .data.backendAvailable == true
    and .data.backendHealth.available == true
    and .data.pendingExternalUpserts == 0
    and .data.pendingExternalDeletes == 0
  ' >/dev/null || {
    echo "FAIL: required external Agent projection is unavailable or has pending repairs" >&2; exit 1;
  }
fi

# These endpoints return code=0 whether there is work to replay, which is the
# safe expectation for a clean smoke environment.
curl_json -X POST "$BASE_URL/app-api/agent/knowledge/reindex/async/retry?jobId=$job" -d '{}' | jq -e '.code == 0' >/dev/null
curl_json "$BASE_URL/app-api/agent/knowledge/reindex/async/dead-letters?limit=5" | jq -e '.code == 0 and (.data | type == "array")' >/dev/null
curl_json "$BASE_URL/app-api/agent/usage/summary" | jq -e '.code == 0' >/dev/null
curl_json "$BASE_URL/app-api/agent/audit/recent?limit=5" | jq -e '.code == 0 and (.data | type == "array")' >/dev/null
curl_json "$BASE_URL/app-api/agent/tools/platform-rules" | jq -e '.code == 0' >/dev/null

VERIFY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bash "$VERIFY_DIR/agent-evaluation-smoke.sh"
if [[ "${CURMERCE_AGENT_RUN_PROVIDER_SMOKE:-false}" == "true" ]]; then
  bash "$VERIFY_DIR/agent-provider-smoke.sh"
fi
printf 'PASS: Agent knowledge, async ingestion, audit, rules, and evaluation integration verified\n'

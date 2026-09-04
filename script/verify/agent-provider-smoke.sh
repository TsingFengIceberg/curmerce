#!/usr/bin/env bash
set -Eeuo pipefail

# Authenticated provider smoke check. It verifies both the provider model list
# and one bounded chat request, but never prints provider responses or tokens.
BASE_URL="${CURMERCE_AGENT_URL:-http://127.0.0.1:48084}"
TOKEN="${CURMERCE_INTERNAL_TOKEN:?set CURMERCE_INTERNAL_TOKEN}"
MODEL_REQUIRED="${CURMERCE_AGENT_PROVIDER_REQUIRED:-true}"
EMBEDDING_REQUIRED="${CURMERCE_AGENT_EMBEDDING_REQUIRED:-false}"

if [[ "${CURMERCE_AGENT_PROVIDER_MODE:-mock}" == "real" && "${CURMERCE_AGENT_ALLOW_REAL_PROVIDER:-false}" != "true" ]]; then
  echo "Refusing real provider smoke. Set CURMERCE_AGENT_ALLOW_REAL_PROVIDER=true deliberately." >&2
  exit 2
fi

curl_json() {
  curl --fail --silent --show-error --max-time "${CURMERCE_AGENT_CURL_TIMEOUT_SECONDS:-15}" \
    -H "X-Curmerce-Internal-Token: $TOKEN" -H 'Content-Type: application/json' "$@"
}

readiness="$(curl_json "$BASE_URL/app-api/agent/model/readiness")"
if [[ "$MODEL_REQUIRED" == "true" ]]; then
  printf '%s' "$readiness" | jq -e '.data.model.enabled == true and .data.model.ready == true' >/dev/null \
    || { printf 'FAIL: configured Agent model provider is not ready\n' >&2; exit 1; }
fi
if [[ "$EMBEDDING_REQUIRED" == "true" ]]; then
  printf '%s' "$readiness" | jq -e '.data.embedding.enabled == true and .data.embedding.ready == true' >/dev/null \
    || { printf 'FAIL: configured Agent embedding provider is not ready\n' >&2; exit 1; }
fi

smoke="$(curl_json -X POST "$BASE_URL/app-api/agent/model/smoke" -d '{}')"
if [[ "$MODEL_REQUIRED" == "true" ]]; then
  printf '%s' "$smoke" | jq -e '.data.enabled == true and .data.ready == true and .data.answerReceived == true' >/dev/null \
    || { printf 'FAIL: bounded Agent model request did not succeed\n' >&2; exit 1; }
fi
if [[ "${CURMERCE_AGENT_PROVIDER_MODE:-mock}" == "mock" && "$MODEL_REQUIRED" == "true" ]]; then
  # The deterministic local mock first requests the read-only platform-rules
  # tool, then returns a final answer after receiving the tool transcript.
  # Real providers are intentionally not required to make this exact choice.
  assist="$(curl_json -X POST "$BASE_URL/app-api/agent/assist" \
    -d '{"query":"请说明平台交易规则","conversationId":"provider-smoke"}')"
  printf '%s' "$assist" | jq -e '
    .code == 0 and .data.modelBacked == true
    and (.data.toolCalls | type == "array") and (.data.toolCalls | length) >= 1
    and (.data.toolResults | type == "array") and (.data.toolResults | length) >= 1
  ' >/dev/null || { printf 'FAIL: mock Agent tool-call round trip did not succeed\n' >&2; exit 1; }
fi
printf 'PASS: Agent provider readiness and bounded request verified\n'

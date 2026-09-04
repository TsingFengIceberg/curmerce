#!/usr/bin/env bash
set -Eeuo pipefail

# Unified Agent acceptance entry point. It checks the six implemented
# capability groups through public/internal contracts without printing tokens,
# prompts, model answers, or persisted user content. Run against a disposable
# local Agent instance; provider and JDBC rule checks are explicitly opt-in.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BASE_URL="${CURMERCE_AGENT_URL:-http://127.0.0.1:48084}"
TOKEN="${CURMERCE_INTERNAL_TOKEN:?set CURMERCE_INTERNAL_TOKEN}"
RUN_PROVIDER="${CURMERCE_AGENT_RUN_PROVIDER_SMOKE:-false}"
RUN_PROVIDER_CIRCUIT="${CURMERCE_AGENT_RUN_PROVIDER_CIRCUIT_SMOKE:-false}"
RUN_RULE_ROLLBACK="${CURMERCE_AGENT_RUN_RULE_ROLLBACK_SMOKE:-false}"
RUN_KAFKA_PROJECTION="${CURMERCE_AGENT_RUN_KAFKA_PROJECTION_SMOKE:-false}"
BASE_URL="${BASE_URL%/}"

[[ "$RUN_PROVIDER" == true || "$RUN_PROVIDER" == false ]] || { echo "CURMERCE_AGENT_RUN_PROVIDER_SMOKE must be true or false" >&2; exit 2; }
[[ "$RUN_PROVIDER_CIRCUIT" == true || "$RUN_PROVIDER_CIRCUIT" == false ]] || { echo "CURMERCE_AGENT_RUN_PROVIDER_CIRCUIT_SMOKE must be true or false" >&2; exit 2; }
[[ "$RUN_RULE_ROLLBACK" == true || "$RUN_RULE_ROLLBACK" == false ]] || { echo "CURMERCE_AGENT_RUN_RULE_ROLLBACK_SMOKE must be true or false" >&2; exit 2; }
[[ "$RUN_KAFKA_PROJECTION" == true || "$RUN_KAFKA_PROJECTION" == false ]] || { echo "CURMERCE_AGENT_RUN_KAFKA_PROJECTION_SMOKE must be true or false" >&2; exit 2; }

curl_json() {
  curl --fail --silent --show-error --max-time "${CURMERCE_AGENT_CURL_TIMEOUT_SECONDS:-15}" \
    -H "X-Curmerce-Internal-Token: $TOKEN" -H 'Content-Type: application/json' "$@"
}

# 1. Provider boundary and model capability contract. Real or mock provider
# probing is opt-in because it may consume external quota.
capabilities="$(curl_json "$BASE_URL/app-api/agent/capabilities")"
printf '%s' "$capabilities" | jq -e '
  .code == 0 and .data.readOnlyByDefault == true
  and .data.sensitiveActionsRequireConfirmation == true
  and (["product-discovery","community-experience-retrieval","order-status","vector-knowledge","tool-registry","policy-quota","grounding-warnings","usage-summary"] - .data.capabilities | length) == 0
' >/dev/null
if [[ "$RUN_PROVIDER" == true ]]; then
  CURMERCE_AGENT_PROVIDER_REQUIRED="${CURMERCE_AGENT_PROVIDER_REQUIRED:-true}" \
    bash "$ROOT_DIR/script/verify/agent-provider-smoke.sh"
fi
if [[ "$RUN_PROVIDER_CIRCUIT" == true ]]; then
  bash "$ROOT_DIR/script/verify/agent-provider-circuit-smoke.sh"
fi

# 2 and 3. RAG replacement, deletion, rebuild, retry and external-projection
# repair. The real shared Kafka Topic check is opt-in because it writes
# disposable events to an active broker.
bash "$ROOT_DIR/script/verify/agent-integration-smoke.sh"
if [[ "$RUN_KAFKA_PROJECTION" == true ]]; then
  bash "$ROOT_DIR/script/verify/agent-kafka-projection-smoke.sh"
fi

# 4. Deterministic safety, injection and grounding evaluation.
bash "$ROOT_DIR/script/verify/agent-evaluation-smoke.sh"

# 5. Allow-list and confirmation contract. The registry is read-only; the
# sensitive refund tool must be present and marked sensitive.
tools="$(curl_json "$BASE_URL/app-api/agent/tools/registry")"
printf '%s' "$tools" | jq -e '.code == 0 and ([.data[] | select(.name == "refund-request" and .sensitive == true)] | length) == 1' >/dev/null

# 6. Audit, usage, quotas/metrics and knowledge queue operational surfaces.
curl_json "$BASE_URL/app-api/agent/usage/summary" | jq -e '.code == 0' >/dev/null
curl_json "$BASE_URL/app-api/agent/usage/scopes" | jq -e '.code == 0 and (.data | type == "object")' >/dev/null
curl_json "$BASE_URL/app-api/agent/usage/report?days=1" | jq -e '.code == 0' >/dev/null
curl_json "$BASE_URL/app-api/agent/audit/recent?limit=20" | jq -e '.code == 0 and (.data | type == "array")' >/dev/null
status="$(curl_json "$BASE_URL/app-api/agent/knowledge/status")"
printf '%s' "$status" | jq -e '
  .code == 0 and (.data.documents | type == "number")
  and (.data.queueDepth | type == "number") and (.data.pendingDepth | type == "number")
  and (.data.retryWaiting | type == "number") and (.data.deadLetterDepth | type == "number")
' >/dev/null
curl_json "$BASE_URL/app-api/agent/tools/platform-rules" | jq -e '.code == 0 and (.data | type == "object")' >/dev/null

if [[ "$RUN_RULE_ROLLBACK" == true ]]; then
  # This changes the optional JDBC rule store and is therefore never enabled
  # by default. The helper validates compare-and-set and monotonic rollback.
  bash "$ROOT_DIR/script/verify/agent-rule-rollback-smoke.sh"
fi

echo "PASS: Agent six-capability reliability smoke"

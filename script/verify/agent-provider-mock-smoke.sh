#!/usr/bin/env bash
set -Eeuo pipefail

# This is the no-secret acceptance path for an OpenAI-compatible Agent
# provider. Start the Agent service with CURMERCE_AGENT_MODEL_ENABLED=true,
# CURMERCE_AGENT_MODEL_BASE_URL=http://127.0.0.1:${CURMERCE_AGENT_MOCK_PROVIDER_PORT:-48185}/v1,
# and CURMERCE_AGENT_MODEL_API_KEY=mock before invoking this script.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MOCK_PORT="${CURMERCE_AGENT_MOCK_PROVIDER_PORT:-48185}"
MOCK_HOST="${CURMERCE_AGENT_MOCK_PROVIDER_HOST:-127.0.0.1}"
START_MOCK="${CURMERCE_AGENT_START_MOCK_PROVIDER:-true}"
MOCK_PID=""

cleanup() {
  if [[ -n "$MOCK_PID" ]]; then kill "$MOCK_PID" 2>/dev/null || true; wait "$MOCK_PID" 2>/dev/null || true; fi
}
trap cleanup EXIT INT TERM

if [[ "$START_MOCK" == "true" ]]; then
  command -v node >/dev/null || { echo "node is required for the mock provider" >&2; exit 2; }
  CURMERCE_AGENT_MOCK_PROVIDER_PORT="$MOCK_PORT" CURMERCE_AGENT_MOCK_PROVIDER_HOST="$MOCK_HOST" \
    node "$ROOT_DIR/script/verify/openai-compatible-mock-provider.mjs" >/tmp/curmerce-agent-mock-provider.log 2>&1 &
  MOCK_PID="$!"
  for _ in $(seq 1 30); do
    if curl --fail --silent --show-error "http://$MOCK_HOST:$MOCK_PORT/v1/models" >/dev/null 2>&1; then break; fi
    sleep 0.2
  done
  curl --fail --silent --show-error "http://$MOCK_HOST:$MOCK_PORT/v1/models" >/dev/null || {
    echo "mock provider did not become ready; inspect /tmp/curmerce-agent-mock-provider.log" >&2; exit 1;
  }
fi

CURMERCE_AGENT_PROVIDER_MODE=mock CURMERCE_AGENT_PROVIDER_REQUIRED=true \
  bash "$ROOT_DIR/script/verify/agent-provider-smoke.sh"
printf 'PASS: Agent mock provider acceptance verified\n'

#!/usr/bin/env bash
set -Eeuo pipefail

# Verifies the model circuit's OPEN -> HALF_OPEN -> CLOSED lifecycle against
# the disposable local OpenAI-compatible provider.  The script never prints
# model output or credentials.  Start Agent with model support enabled and
# point it at the mock provider before running this check.
BASE_URL="${CURMERCE_AGENT_URL:-http://127.0.0.1:48084}"
TOKEN="${CURMERCE_INTERNAL_TOKEN:?set CURMERCE_INTERNAL_TOKEN}"
FAILURES="${CURMERCE_AGENT_CIRCUIT_FAILURES:-4}"
WAIT_SECONDS="${CURMERCE_AGENT_MODEL_CIRCUIT_WAIT_SECONDS:-16}"
[[ "$FAILURES" =~ ^[3-9][0-9]*$ ]] || { echo "FAIL: failures must be >= 3" >&2; exit 2; }
[[ "$WAIT_SECONDS" =~ ^[1-9][0-9]*$ ]] || { echo "FAIL: wait seconds must be positive" >&2; exit 2; }

curl_json() {
  curl --fail --silent --show-error --max-time "${CURMERCE_AGENT_CURL_TIMEOUT_SECONDS:-20}" \
    -H "X-Curmerce-Internal-Token: $TOKEN" -H 'Content-Type: application/json' "$@"
}

state() { curl_json "$BASE_URL/app-api/agent/model/circuit" | jq -r '.data.state // "UNKNOWN"'; }
smoke() { curl_json -X POST "$BASE_URL/app-api/agent/model/smoke" -d '{}' >/dev/null; }

# The mock provider consumes one sequence entry per HTTP attempt.  The model
# adapter retries a transient response up to three times, while the circuit
# records one failure for the completed model call.  A finite sequence is
# therefore required; a permanently failing mock can never demonstrate
# HALF_OPEN -> CLOSED recovery.
SEQUENCE="${CURMERCE_AGENT_MOCK_CHAT_FAILURE_SEQUENCE:-}"
if [[ -z "$SEQUENCE" ]]; then
  echo "FAIL: set CURMERCE_AGENT_MOCK_CHAT_FAILURE_SEQUENCE to a finite sequence (for example $(printf '500,%.0s' $(seq 1 $((FAILURES * 3))) | sed 's/,$//'))" >&2
  exit 2
fi
sequence_count="$(awk -F',' '{print NF}' <<<"$SEQUENCE")"
(( sequence_count >= FAILURES * 3 )) || {
  echo "FAIL: mock failure sequence needs at least $((FAILURES * 3)) entries because provider calls retry three times" >&2
  exit 2
}

before="$(state)"
for _ in $(seq 1 "$FAILURES"); do
  smoke || true
done
opened="$(state)"
[[ "$opened" == "OPEN" ]] || { echo "FAIL: circuit did not open (before=$before after=$opened)" >&2; exit 1; }

# While OPEN the provider must not be called.  A smoke request is still a
# bounded HTTP response; the state remains OPEN and the not-permitted metric
# increases, which operators can inspect through the circuit endpoint.
smoke || true
still_open="$(state)"
[[ "$still_open" == "OPEN" ]] || { echo "FAIL: open circuit changed unexpectedly: $still_open" >&2; exit 1; }

deadline=$((SECONDS + WAIT_SECONDS + 5)); half=""
while (( SECONDS < deadline )); do
  half="$(state)"
  [[ "$half" == "HALF_OPEN" ]] && break
  sleep 1
done
[[ "$half" == "HALF_OPEN" ]] || { echo "FAIL: circuit did not enter HALF_OPEN after wait: $half" >&2; exit 1; }

for _ in 1 2; do smoke || true; done
closed="$(state)"
[[ "$closed" == "CLOSED" ]] || { echo "FAIL: circuit did not recover to CLOSED: $closed" >&2; exit 1; }
echo "PASS: Agent model circuit OPEN -> HALF_OPEN -> CLOSED verified"

#!/usr/bin/env bash
set -Eeuo pipefail

# Verify the real Kafka -> Agent knowledge path with isolated aggregate IDs.
# The script writes only unique disposable events and ends with higher-version
# hidden events so the smoke documents are removed from the knowledge store.
AGENT_URL="${CURMERCE_AGENT_URL:-http://127.0.0.1:48084}"
KAFKA_TOPIC="${CURMERCE_AGENT_KAFKA_TOPIC:-curmerce.agent.events.v1}"
KAFKA_BOOTSTRAP_SERVERS="${CURMERCE_AGENT_KAFKA_BOOTSTRAP_SERVERS:-127.0.0.1:19092}"
KAFKA_COMPOSE_FILE="${CURMERCE_AGENT_KAFKA_COMPOSE_FILE:-deploy/cloud/compose.yaml}"
POLL_SECONDS="${CURMERCE_AGENT_KAFKA_POLL_SECONDS:-30}"
SUFFIX="$(date +%s)-$$"
PRODUCT_ID="$((800000000 + ($$ % 100000000)))"
POST_ID="$((900000000 + ($$ % 100000000)))"
EVENT_BASE="$((($(date +%s) * 1000) + ($$ % 1000)))"
PRODUCT_MARKER="agent-kafka-product-$SUFFIX"
POST_MARKER="agent-kafka-post-$SUFFIX"
AGENT_URL="${AGENT_URL%/}"
PRODUCT_EVENT_PUBLISHED=false
POST_EVENT_PUBLISHED=false
CLEANED_UP=false

[[ "$POLL_SECONDS" =~ ^[0-9]+$ ]] && (( POLL_SECONDS > 0 )) || {
  echo "CURMERCE_AGENT_KAFKA_POLL_SECONDS must be a positive integer" >&2
  exit 2
}

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

publish() {
  local message="$1"
  if [[ -n "${CURMERCE_AGENT_KAFKA_PRODUCER:-}" ]]; then
    printf '%s\n' "$message" | "${CURMERCE_AGENT_KAFKA_PRODUCER}" \
      --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" --topic "$KAFKA_TOPIC"
    return
  fi
  if command -v docker >/dev/null 2>&1 && [[ -f "$KAFKA_COMPOSE_FILE" ]]; then
    printf '%s\n' "$message" | docker compose -f "$KAFKA_COMPOSE_FILE" exec -T kafka \
      /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:19092 --topic "$KAFKA_TOPIC"
    return
  fi
  printf 'FAIL: set CURMERCE_AGENT_KAFKA_PRODUCER or start the local Compose Kafka service\n' >&2
  return 1
}

search_has() {
  local source="$1" marker="$2" encoded result
  encoded="$(jq -nr --arg value "$marker" '$value | @uri')"
  result="$(curl --fail --silent --show-error --max-time 15 \
    "$AGENT_URL/app-api/agent/knowledge/search?query=$encoded&limit=20&source=$source")"
  printf '%s' "$result" | jq -e --arg marker "$marker" \
    '[.data[]?.text | contains($marker)] | any' >/dev/null
}

search_lacks() {
  local source="$1" marker="$2" encoded result
  encoded="$(jq -nr --arg value "$marker" '$value | @uri')"
  result="$(curl --fail --silent --show-error --max-time 15 \
    "$AGENT_URL/app-api/agent/knowledge/search?query=$encoded&limit=20&source=$source")"
  printf '%s' "$result" | jq -e --arg marker "$marker" \
    '[.data[]?.text | contains($marker)] | any | not' >/dev/null
}

wait_for_present() {
  local source="$1" marker="$2" description="$3" deadline=$((SECONDS + POLL_SECONDS))
  while (( SECONDS < deadline )); do
    if search_has "$source" "$marker"; then return; fi
    sleep 1
  done
  fail "timed out waiting for $description"
}

wait_for_absent() {
  local source="$1" marker="$2" description="$3" deadline=$((SECONDS + POLL_SECONDS))
  while (( SECONDS < deadline )); do
    if search_lacks "$source" "$marker"; then return; fi
    sleep 1
  done
  fail "timed out waiting for $description"
}

product_event() {
  local event_id="$1" name="$2" audit_status="$3" sale_status="$4"
  jq -nc --argjson eventId "$event_id" --argjson productId "$PRODUCT_ID" --arg name "$name" \
    --argjson auditStatus "$audit_status" --argjson saleStatus "$sale_status" '
      {eventId:$eventId,eventType:"PRODUCT_CHANGED",eventKey:("product:" + ($productId|tostring)),
       payload:{productId:$productId,name:$name,description:$name,auditStatus:$auditStatus,saleStatus:$saleStatus,
       skus:[{code:"agent-smoke-sku",price:1999,stock:3}]}}'
}

post_event() {
  local event_id="$1" title="$2" status="$3"
  jq -nc --argjson eventId "$event_id" --argjson postId "$POST_ID" --arg title "$title" --argjson status "$status" '
      {eventId:$eventId,eventType:"POST_CHANGED",eventKey:("post:" + ($postId|tostring)),
       payload:{postId:$postId,title:$title,content:$title,status:$status,topics:["agent-smoke"],productIds:[]}}'
}

cleanup() {
  # Kafka can legitimately be unavailable while the test is failing, so this
  # is intentionally best-effort. A successful prior publish proves the same
  # producer path is normally usable and makes the cleanup likely to converge.
  [[ "$CLEANED_UP" == true ]] && return
  if [[ "$PRODUCT_EVENT_PUBLISHED" == true ]]; then
    publish "$(product_event "$((EVENT_BASE + 2))" "$PRODUCT_MARKER" 2 0)" >/dev/null 2>&1 || true
  fi
  if [[ "$POST_EVENT_PUBLISHED" == true ]]; then
    publish "$(post_event "$((EVENT_BASE + 3))" "$POST_MARKER" 2)" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

command -v jq >/dev/null || fail "jq is required"
curl --fail --silent --show-error --max-time 10 "$AGENT_URL/actuator/health" \
  | jq -e '.status == "UP"' >/dev/null || fail "Agent service is unavailable"

# Visible events prove that the shared topic and consumer group are live.
publish "$(product_event "$EVENT_BASE" "$PRODUCT_MARKER" 2 1)"
PRODUCT_EVENT_PUBLISHED=true
publish "$(post_event "$((EVENT_BASE + 1))" "$POST_MARKER" 1)"
POST_EVENT_PUBLISHED=true
wait_for_present product "$PRODUCT_MARKER" "product Kafka projection"
wait_for_present community "$POST_MARKER" "post Kafka projection"

# Lower event IDs must not replace the already projected newer state.
publish "$(product_event "$((EVENT_BASE - 1))" "old-$PRODUCT_MARKER" 2 1)"
publish "$(post_event "$EVENT_BASE" "old-$POST_MARKER" 1)"
sleep 2
search_lacks product "old-$PRODUCT_MARKER" || fail "older product event replaced newer projection"
search_lacks community "old-$POST_MARKER" || fail "older post event replaced newer projection"

# Higher event IDs that make the source invisible must remove stale documents.
publish "$(product_event "$((EVENT_BASE + 2))" "$PRODUCT_MARKER" 2 0)"
publish "$(post_event "$((EVENT_BASE + 3))" "$POST_MARKER" 2)"
wait_for_absent product "$PRODUCT_MARKER" "product removal projection"
wait_for_absent community "$POST_MARKER" "post removal projection"
CLEANED_UP=true

printf 'PASS: Agent Kafka product/post projection, checkpoint, and deletion verified\n'

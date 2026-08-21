#!/usr/bin/env bash
set -euo pipefail

# Login-state acceptance for the community loop. Credentials are supplied by
# the caller and are never printed. Example:
# CURMERCE_BUYER_MOBILE=... CURMERCE_BUYER_PASSWORD=... \
# CURMERCE_ADMIN_USERNAME=... CURMERCE_ADMIN_PASSWORD=... ./script/community-acceptance.sh

: "${CURMERCE_BUYER_MOBILE:?set CURMERCE_BUYER_MOBILE}"
: "${CURMERCE_BUYER_PASSWORD:?set CURMERCE_BUYER_PASSWORD}"
: "${CURMERCE_ADMIN_USERNAME:?set CURMERCE_ADMIN_USERNAME}"
: "${CURMERCE_ADMIN_PASSWORD:?set CURMERCE_ADMIN_PASSWORD}"

BASE_URL="${CURMERCE_BASE_URL:-http://127.0.0.1:48080}"
TENANT_ID="${CURMERCE_TENANT_ID:-1}"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

request() {
  local token="$1" method="$2" path="$3" body="${4:-}"
  local output="$tmp_dir/response.json"
  if [[ -n "$body" ]]; then
    curl -fsS -X "$method" "$BASE_URL$path" -H "tenant-id: $TENANT_ID" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' -d "$body" >"$output"
  else
    curl -fsS -X "$method" "$BASE_URL$path" -H "tenant-id: $TENANT_ID" -H "Authorization: Bearer $token" >"$output"
  fi
  jq -e '.code == 0' "$output" >/dev/null || { jq -c '{code,msg}' "$output" >&2; return 1; }
  cat "$output"
}

buyer_login="$(curl -fsS -X POST "$BASE_URL/app-api/member/auth/login" -H "tenant-id: $TENANT_ID" -H 'Content-Type: application/json' -d "$(jq -nc --arg mobile "$CURMERCE_BUYER_MOBILE" --arg password "$CURMERCE_BUYER_PASSWORD" '{mobile:$mobile,password:$password}')")"
buyer_token="$(jq -r '.data.accessToken' <<<"$buyer_login")"
admin_login="$(curl -fsS -X POST "$BASE_URL/admin-api/system/auth/login" -H "tenant-id: $TENANT_ID" -H 'Content-Type: application/json' -d "$(jq -nc --arg username "$CURMERCE_ADMIN_USERNAME" --arg password "$CURMERCE_ADMIN_PASSWORD" '{username:$username,password:$password}')")"
admin_token="$(jq -r '.data.accessToken' <<<"$admin_login")"

title="Community acceptance $(date +%s)"
created="$(request "$buyer_token" POST /app-api/community/post/create "$(jq -nc --arg title "$title" '{title:$title,content:"Automated acceptance content",topics:["acceptance"],mediaUrls:[],productIds:[]}')")"
post_id="$(jq -r '.data' <<<"$created")"
request "$buyer_token" PUT /app-api/community/post/update "$(jq -nc --argjson id "$post_id" --arg title "$title updated" '{id:$id,title:$title,content:"Updated acceptance content",topics:["acceptance"],mediaUrls:[],productIds:[]}')" >/dev/null
request "$buyer_token" PUT "/app-api/community/post/submit?id=$post_id" >/dev/null
request "$buyer_token" GET "/app-api/community/post/get?id=$post_id" >/dev/null
request "$buyer_token" POST /app-api/community/comment/create "$(jq -nc --argjson postId "$post_id" '{postId:$postId,content:"Acceptance comment"}')" >/dev/null
request "$buyer_token" PUT /app-api/community/post/reaction "$(jq -nc --argjson postId "$post_id" '{postId:$postId,type:1,active:true}')" >/dev/null
request "$buyer_token" PUT /app-api/community/post/reaction "$(jq -nc --argjson postId "$post_id" '{postId:$postId,type:1,active:true}')" >/dev/null
request "$buyer_token" GET "/app-api/community/post/favorites?pageNo=1&pageSize=10" >/dev/null
request "$buyer_token" GET "/app-api/community/post/following?pageNo=1&pageSize=10" >/dev/null
report="$(request "$buyer_token" POST /app-api/community/report/create "$(jq -nc --argjson postId "$post_id" '{postId:$postId,reason:"Automated acceptance report"}')")"
report_id="$(jq -r '.data' <<<"$report")"
request "$admin_token" PUT /admin-api/community/report/review "$(jq -nc --argjson id "$report_id" '{id:$id,status:2,remark:"Acceptance report rejected"}')" >/dev/null
request "$admin_token" PUT /admin-api/community/post/status "$(jq -nc --argjson id "$post_id" '{id:$id,status:2}')" >/dev/null
request "$admin_token" PUT /admin-api/community/post/status "$(jq -nc --argjson id "$post_id" '{id:$id,status:1}')" >/dev/null
printf 'Community login-state acceptance passed for post %s.\n' "$post_id"

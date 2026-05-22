#!/usr/bin/env bash
set -euo pipefail

# Reusable API smoke + correctness test suite for Knowledge Copilot.
# Exits non-zero when any check fails.
#
# Env overrides:
#   API_BASE_URL=http://localhost:8080
#   KEYCLOAK_BASE_URL=http://localhost:8081
#   KEYCLOAK_REALM=knowledge-copilot
#   KEYCLOAK_CLIENT_ID=knowledge-copilot-api
#   USER_USERNAME=kc_user
#   USER_PASSWORD=user123
#   ADMIN_USERNAME=kc_admin
#   ADMIN_PASSWORD=admin123
#   CHROMA_BASE_URL=http://localhost:8000
#   RUN_RATE_LIMIT_STRESS=true

for dep in curl jq; do
  if ! command -v "$dep" >/dev/null 2>&1; then
    echo "Missing dependency: $dep" >&2
    exit 2
  fi
done

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
KEYCLOAK_BASE_URL="${KEYCLOAK_BASE_URL:-http://localhost:8081}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-knowledge-copilot}"
KEYCLOAK_CLIENT_ID="${KEYCLOAK_CLIENT_ID:-knowledge-copilot-api}"

USER_USERNAME="${USER_USERNAME:-kc_user}"
USER_PASSWORD="${USER_PASSWORD:-user123}"
ADMIN_USERNAME="${ADMIN_USERNAME:-kc_admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"
CHROMA_BASE_URL="${CHROMA_BASE_URL:-http://localhost:8000}"
RUN_RATE_LIMIT_STRESS="${RUN_RATE_LIMIT_STRESS:-true}"

TMP_DIR="$(mktemp -d)"
REPORT_FILE="$TMP_DIR/report.json"
trap 'rm -rf "$TMP_DIR"' EXIT
echo '[]' > "$REPORT_FILE"

PASS=0
FAIL=0

record() {
  local name="$1"
  local ok="$2"
  local detail="$3"
  local status="$4"

  jq \
    --arg name "$name" \
    --argjson ok "$ok" \
    --arg detail "$detail" \
    --arg status "$status" \
    '. += [{name:$name,ok:$ok,detail:$detail,status:$status}]' \
    "$REPORT_FILE" > "$REPORT_FILE.tmp"
  mv "$REPORT_FILE.tmp" "$REPORT_FILE"

  if [[ "$ok" == "true" ]]; then
    PASS=$((PASS + 1))
  else
    FAIL=$((FAIL + 1))
  fi
}

http_call() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  local token="${4:-}"
  local out_file="$5"

  local args=(-s -o "$out_file" -w '%{http_code}' -X "$method")
  args+=(-H "Content-Type: application/json")
  if [[ -n "$token" ]]; then
    args+=(-H "Authorization: Bearer $token")
  fi
  if [[ -n "$body" ]]; then
    args+=(--data "$body")
  fi
  args+=("$url")

  curl "${args[@]}"
}

token_for() {
  local username="$1"
  local password="$2"
  curl -s --location \
    "$KEYCLOAK_BASE_URL/realms/$KEYCLOAK_REALM/protocol/openid-connect/token" \
    --header 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "client_id=$KEYCLOAK_CLIENT_ID" \
    --data-urlencode 'grant_type=password' \
    --data-urlencode "username=$username" \
    --data-urlencode "password=$password" | jq -r '.access_token // empty'
}

USER_TOKEN="$(token_for "$USER_USERNAME" "$USER_PASSWORD")"
ADMIN_TOKEN="$(token_for "$ADMIN_USERNAME" "$ADMIN_PASSWORD")"

if [[ -n "$USER_TOKEN" ]]; then
  record "Generate user token" true "length=${#USER_TOKEN}" "200"
else
  record "Generate user token" false "missing access_token" "200"
fi

if [[ -n "$ADMIN_TOKEN" ]]; then
  record "Generate admin token" true "length=${#ADMIN_TOKEN}" "200"
else
  record "Generate admin token" false "missing access_token" "200"
fi

# ChromaDB health preflight (supports common v1/v2 heartbeat paths)
CHROMA_HEALTH_CODE="$(curl -s -o "$TMP_DIR/chroma_health.json" -w '%{http_code}' "$CHROMA_BASE_URL/api/v1/heartbeat")"
if [[ "$CHROMA_HEALTH_CODE" != "200" ]]; then
  CHROMA_HEALTH_CODE="$(curl -s -o "$TMP_DIR/chroma_health.json" -w '%{http_code}' "$CHROMA_BASE_URL/api/v2/heartbeat")"
fi
if [[ "$CHROMA_HEALTH_CODE" == "200" ]]; then
  record "GET ChromaDB heartbeat" true "baseUrl=$CHROMA_BASE_URL" "$CHROMA_HEALTH_CODE"
else
  record "GET ChromaDB heartbeat" false "Chroma unreachable at $CHROMA_BASE_URL" "$CHROMA_HEALTH_CODE"
fi

# Health public
HCODE="$(curl -s -o "$TMP_DIR/health.txt" -w '%{http_code}' "$API_BASE_URL/api/assistant/health")"
HBODY="$(cat "$TMP_DIR/health.txt")"
if [[ "$HCODE" == "200" && "$HBODY" == "OK" ]]; then
  record "GET /api/assistant/health public" true "returns OK" "$HCODE"
else
  record "GET /api/assistant/health public" false "body=$HBODY" "$HCODE"
fi

# Ask unauthorized
CODE="$(http_call POST "$API_BASE_URL/api/assistant/ask" '{"question":"test"}' "" "$TMP_DIR/ask_noauth.json")"
if [[ "$CODE" == "401" ]]; then
  record "POST /api/assistant/ask unauthorized" true "requires JWT" "$CODE"
else
  record "POST /api/assistant/ask unauthorized" false "expected 401 got $CODE" "$CODE"
fi

# Ask user/admin
Q='How does ingestion flow work?'
CODE="$(http_call POST "$API_BASE_URL/api/assistant/ask" "{\"question\":\"$Q\"}" "$USER_TOKEN" "$TMP_DIR/ask_user.json")"
RID="$(jq -r '.responseId // empty' "$TMP_DIR/ask_user.json")"
if [[ "$CODE" == "200" ]] && jq -e '(.answer|type=="string") and (.answer|length>0) and (.citations|type=="array") and (.insufficientEvidence|type=="boolean")' "$TMP_DIR/ask_user.json" >/dev/null; then
  record "POST /api/assistant/ask user" true "responseId=$RID" "$CODE"
else
  record "POST /api/assistant/ask user" false "body=$(cat "$TMP_DIR/ask_user.json")" "$CODE"
fi

CODE="$(http_call POST "$API_BASE_URL/api/assistant/ask" "{\"question\":\"$Q\"}" "$ADMIN_TOKEN" "$TMP_DIR/ask_admin.json")"
RID_ADMIN="$(jq -r '.responseId // empty' "$TMP_DIR/ask_admin.json")"
if [[ "$CODE" == "200" && -n "$RID_ADMIN" ]]; then
  record "POST /api/assistant/ask admin" true "responseId=$RID_ADMIN" "$CODE"
else
  record "POST /api/assistant/ask admin" false "body=$(cat "$TMP_DIR/ask_admin.json")" "$CODE"
fi

# Filter checks
CODE="$(http_call POST "$API_BASE_URL/api/assistant/ask" '{"question":"How does ingestion flow work?","sourceTypes":["LOCAL_FILE_SYSTEM"]}' "$USER_TOKEN" "$TMP_DIR/ask_local.json")"
if [[ "$CODE" == "200" ]] && jq -e 'if (.citations|length)==0 then true else ([.citations[].sourceType=="LOCAL_FILE_SYSTEM"]|all) end' "$TMP_DIR/ask_local.json" >/dev/null; then
  record "POST /api/assistant/ask sourceTypes=LOCAL_FILE_SYSTEM" true "citations=$(jq -r '.citations|length' "$TMP_DIR/ask_local.json")" "$CODE"
else
  record "POST /api/assistant/ask sourceTypes=LOCAL_FILE_SYSTEM" false "body=$(cat "$TMP_DIR/ask_local.json")" "$CODE"
fi

CODE="$(http_call POST "$API_BASE_URL/api/assistant/ask" '{"question":"How does ingestion flow work?","sourceTypes":["JIRA"]}' "$USER_TOKEN" "$TMP_DIR/ask_jira.json")"
if [[ "$CODE" == "200" ]] && jq -e 'if (.citations|length)==0 then (.insufficientEvidence==true) else ([.citations[].sourceType=="JIRA"]|all) end' "$TMP_DIR/ask_jira.json" >/dev/null; then
  record "POST /api/assistant/ask sourceTypes=JIRA" true "citations=$(jq -r '.citations|length' "$TMP_DIR/ask_jira.json")" "$CODE"
else
  record "POST /api/assistant/ask sourceTypes=JIRA" false "body=$(cat "$TMP_DIR/ask_jira.json")" "$CODE"
fi

CODE="$(http_call POST "$API_BASE_URL/api/assistant/ask" '{"question":"How does ingestion flow work?","updatedAfter":"2099-01-01T00:00:00Z"}' "$USER_TOKEN" "$TMP_DIR/ask_future.json")"
if [[ "$CODE" == "200" ]] && jq -e '(.citations|length)==0 and (.insufficientEvidence==true)' "$TMP_DIR/ask_future.json" >/dev/null; then
  record "POST /api/assistant/ask updatedAfter future" true "no eligible docs" "$CODE"
else
  record "POST /api/assistant/ask updatedAfter future" false "body=$(cat "$TMP_DIR/ask_future.json")" "$CODE"
fi

CODE="$(http_call POST "$API_BASE_URL/api/assistant/ask" '{"question":"How does ingestion flow work?","connectorFilters":{"sourcePathContains":"knowledge-base"}}' "$USER_TOKEN" "$TMP_DIR/ask_conn.json")"
if [[ "$CODE" == "200" ]] && jq -e 'if (.citations|length)==0 then false else ([.citations[].sourcePath|contains("knowledge-base")]|all) end' "$TMP_DIR/ask_conn.json" >/dev/null; then
  record "POST /api/assistant/ask connectorFilters.sourcePathContains" true "citations=$(jq -r '.citations|length' "$TMP_DIR/ask_conn.json")" "$CODE"
else
  record "POST /api/assistant/ask connectorFilters.sourcePathContains" false "body=$(cat "$TMP_DIR/ask_conn.json")" "$CODE"
fi

# Feedback unauthorized
CODE="$(http_call POST "$API_BASE_URL/api/assistant/feedback" '{"responseId":"00000000-0000-0000-0000-000000000001","verdict":"GOOD"}' "" "$TMP_DIR/fb_noauth.json")"
if [[ "$CODE" == "401" ]]; then
  record "POST /api/assistant/feedback unauthorized" true "requires JWT" "$CODE"
else
  record "POST /api/assistant/feedback unauthorized" false "expected 401 got $CODE" "$CODE"
fi

# Feedback upsert correctness (isolated question)
UQ="feedback upsert test $(date +%s)"
CODE="$(http_call POST "$API_BASE_URL/api/assistant/ask" "{\"question\":\"$UQ\"}" "$USER_TOKEN" "$TMP_DIR/ask_uniq.json")"
URID="$(jq -r '.responseId // empty' "$TMP_DIR/ask_uniq.json")"

CODE1="$(http_call POST "$API_BASE_URL/api/assistant/feedback" "{\"responseId\":\"$URID\",\"verdict\":\"GOOD\"}" "$USER_TOKEN" "$TMP_DIR/fb_good.json")"
CODE2="$(http_call POST "$API_BASE_URL/api/assistant/feedback" "{\"responseId\":\"$URID\",\"verdict\":\"BAD\"}" "$USER_TOKEN" "$TMP_DIR/fb_bad.json")"
if [[ "$CODE1" == "200" && "$CODE2" == "200" ]] \
  && jq -e '.goodCount==1 and .badCount==0 and .goodRate==1.0' "$TMP_DIR/fb_good.json" >/dev/null \
  && jq -e '.goodCount==0 and .badCount==1 and .goodRate==0.0' "$TMP_DIR/fb_bad.json" >/dev/null; then
  record "POST /api/assistant/feedback correctness (upsert vote)" true "GOOD->BAD toggles counts" "200/200"
else
  record "POST /api/assistant/feedback correctness (upsert vote)" false "good=$(cat "$TMP_DIR/fb_good.json") bad=$(cat "$TMP_DIR/fb_bad.json")" "200/200"
fi

# Admin RBAC
CODE="$(curl -s -o "$TMP_DIR/reindex_user.txt" -w '%{http_code}' -X POST -H "Authorization: Bearer $USER_TOKEN" "$API_BASE_URL/api/admin/reindex")"
if [[ "$CODE" == "403" ]]; then
  record "POST /api/admin/reindex user forbidden" true "RBAC enforced" "$CODE"
else
  record "POST /api/admin/reindex user forbidden" false "expected 403 got $CODE" "$CODE"
fi

CODE="$(curl -s -o "$TMP_DIR/reindex_admin.txt" -w '%{http_code}' -X POST -H "Authorization: Bearer $ADMIN_TOKEN" "$API_BASE_URL/api/admin/reindex")"
if [[ "$CODE" == "202" && "$(cat "$TMP_DIR/reindex_admin.txt")" == "Reindex triggered" ]]; then
  record "POST /api/admin/reindex admin" true "accepted + message" "$CODE"
else
  record "POST /api/admin/reindex admin" false "body=$(cat "$TMP_DIR/reindex_admin.txt")" "$CODE"
fi

CODE="$(curl -s -o "$TMP_DIR/obs_user.json" -w '%{http_code}' -H "Authorization: Bearer $USER_TOKEN" "$API_BASE_URL/api/admin/observability/summary")"
if [[ "$CODE" == "403" ]]; then
  record "GET /api/admin/observability/summary user forbidden" true "RBAC enforced" "$CODE"
else
  record "GET /api/admin/observability/summary user forbidden" false "expected 403 got $CODE" "$CODE"
fi

CODE="$(curl -s -o "$TMP_DIR/obs_admin.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" "$API_BASE_URL/api/admin/observability/summary")"
if [[ "$CODE" == "200" ]] && jq -e '
  has("inventory") and has("ask") and has("evaluation") and has("feedback") and has("ingestion") and has("security") and has("audit") and
  (.security|type=="object") and (.audit|type=="object") and
  (.security.rateLimitRejectedTotal|type=="number") and
  (.audit.totalEvents|type=="number")
' "$TMP_DIR/obs_admin.json" >/dev/null; then
  record "GET /api/admin/observability/summary admin" true \
    "keys ok, rateLimitRejectedTotal=$(jq -r '.security.rateLimitRejectedTotal' "$TMP_DIR/obs_admin.json"), audit.totalEvents=$(jq -r '.audit.totalEvents' "$TMP_DIR/obs_admin.json")" \
    "$CODE"
else
  record "GET /api/admin/observability/summary admin" false "body=$(cat "$TMP_DIR/obs_admin.json")" "$CODE"
fi

# Evaluation RBAC + correctness
CODE="$(http_call POST "$API_BASE_URL/api/admin/evaluations/golden-questions" '{"question":"rbac check","expectedSources":[]}' "$USER_TOKEN" "$TMP_DIR/golden_user.json")"
if [[ "$CODE" == "403" ]]; then
  record "POST /api/admin/evaluations/golden-questions user forbidden" true "RBAC enforced" "$CODE"
else
  record "POST /api/admin/evaluations/golden-questions user forbidden" false "expected 403 got $CODE" "$CODE"
fi

GQ="eval test $(date +%s)"
CODE="$(http_call POST "$API_BASE_URL/api/admin/evaluations/golden-questions" "{\"question\":\"$GQ\",\"expectedSources\":[\"/app/knowledge-base/a.md\",\" /app/knowledge-base/a.md \",\"\",\"/app/knowledge-base/b.md\"]}" "$ADMIN_TOKEN" "$TMP_DIR/golden_admin.json")"
GID="$(jq -r '.id // empty' "$TMP_DIR/golden_admin.json")"
if [[ "$CODE" == "201" && -n "$GID" ]] && jq -e '.expectedSources|length==2' "$TMP_DIR/golden_admin.json" >/dev/null; then
  record "POST /api/admin/evaluations/golden-questions admin" true "created with expectedSources deduped" "$CODE"
else
  record "POST /api/admin/evaluations/golden-questions admin" false "body=$(cat "$TMP_DIR/golden_admin.json")" "$CODE"
fi

CODE="$(curl -s -o "$TMP_DIR/golden_list.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" "$API_BASE_URL/api/admin/evaluations/golden-questions")"
if [[ "$CODE" == "200" ]] && jq -e --arg id "$GID" '[.[]|select(.id==$id)]|length>=1' "$TMP_DIR/golden_list.json" >/dev/null; then
  record "GET /api/admin/evaluations/golden-questions admin" true "created golden is listed" "$CODE"
else
  record "GET /api/admin/evaluations/golden-questions admin" false "body=$(cat "$TMP_DIR/golden_list.json")" "$CODE"
fi

CODE="$(curl -s -o "$TMP_DIR/run_create.json" -w '%{http_code}' -X POST -H "Authorization: Bearer $ADMIN_TOKEN" "$API_BASE_URL/api/admin/evaluations/runs/$GID")"
RUN_ID="$(jq -r '.id // empty' "$TMP_DIR/run_create.json")"
if [[ "$CODE" == "201" && -n "$RUN_ID" ]] && jq -e --arg gid "$GID" '
  (.goldenQuestionId == $gid) and
  (.goldenQuestion|type=="string") and
  (.retrievedChunks|type=="array") and
  (.citations|type=="array") and
  (.generatedAnswer|type=="string") and
  (.citationCorrectness|type=="number") and
  (.citationCorrectness >= 0 and .citationCorrectness <= 1) and
  (.latencyMs|type=="number") and
  (.modelUsed|type=="string") and
  (.promptVersion|type=="string")
' "$TMP_DIR/run_create.json" >/dev/null; then
  record "POST /api/admin/evaluations/runs/{goldenQuestionId}" true "run payload fields valid" "$CODE"
else
  record "POST /api/admin/evaluations/runs/{goldenQuestionId}" false "body=$(cat "$TMP_DIR/run_create.json")" "$CODE"
fi

CODE="$(curl -s -o "$TMP_DIR/runs_all.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" "$API_BASE_URL/api/admin/evaluations/runs")"
if [[ "$CODE" == "200" ]] && jq -e --arg rid "$RUN_ID" '[.[]|select(.id==$rid)]|length>=1' "$TMP_DIR/runs_all.json" >/dev/null; then
  record "GET /api/admin/evaluations/runs" true "created run is listed" "$CODE"
else
  record "GET /api/admin/evaluations/runs" false "body=$(cat "$TMP_DIR/runs_all.json")" "$CODE"
fi

CODE="$(curl -s -o "$TMP_DIR/runs_filtered.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" "$API_BASE_URL/api/admin/evaluations/runs?goldenQuestionId=$GID")"
if [[ "$CODE" == "200" ]] && jq -e --arg gid "$GID" 'if length==0 then false else ([.[].goldenQuestionId==$gid]|all) end' "$TMP_DIR/runs_filtered.json" >/dev/null; then
  record "GET /api/admin/evaluations/runs?goldenQuestionId=" true "filter respected" "$CODE"
else
  record "GET /api/admin/evaluations/runs?goldenQuestionId=" false "body=$(cat "$TMP_DIR/runs_filtered.json")" "$CODE"
fi

CODE="$(http_call POST "$API_BASE_URL/api/admin/evaluations/runs/$RUN_ID/feedback" '{"score":4,"comment":"solid"}' "$ADMIN_TOKEN" "$TMP_DIR/run_feedback.json")"
if [[ "$CODE" == "200" ]] && jq -e '.userFeedbackScore==4 and .userFeedbackComment=="solid"' "$TMP_DIR/run_feedback.json" >/dev/null; then
  record "POST /api/admin/evaluations/runs/{runId}/feedback" true "feedback persisted" "$CODE"
else
  record "POST /api/admin/evaluations/runs/{runId}/feedback" false "body=$(cat "$TMP_DIR/run_feedback.json")" "$CODE"
fi

# Actuator
CODE="$(curl -s -o "$TMP_DIR/act_health.json" -w '%{http_code}' "$API_BASE_URL/actuator/health")"
if [[ "$CODE" == "200" ]] && jq -e '.status=="UP"' "$TMP_DIR/act_health.json" >/dev/null; then
  record "GET /actuator/health" true "status=UP" "$CODE"
else
  record "GET /actuator/health" false "body=$(cat "$TMP_DIR/act_health.json")" "$CODE"
fi

CODE="$(curl -s -o "$TMP_DIR/act_info.json" -w '%{http_code}' "$API_BASE_URL/actuator/info")"
if [[ "$CODE" == "200" ]] && jq -e 'type=="object"' "$TMP_DIR/act_info.json" >/dev/null; then
  record "GET /actuator/info" true "json object" "$CODE"
else
  record "GET /actuator/info" false "body=$(cat "$TMP_DIR/act_info.json")" "$CODE"
fi

CODE="$(curl -s -o "$TMP_DIR/act_prom.txt" -w '%{http_code}' "$API_BASE_URL/actuator/prometheus")"
if [[ "$CODE" == "200" ]] \
  && grep -q '^kc_' "$TMP_DIR/act_prom.txt" \
  && grep -q 'stage="vector_candidates"' "$TMP_DIR/act_prom.txt" \
  && grep -q 'kc_audit_events_total' "$TMP_DIR/act_prom.txt"; then
  record "GET /actuator/prometheus" true "kc_ metrics + hybrid stage tags + audit metric present" "$CODE"
else
  record "GET /actuator/prometheus" false "missing kc_ or hybrid stage tags" "$CODE"
fi

# Optional stress check: intentionally trigger rate limiting and verify 429 behavior + metrics/summary updates
if [[ "$RUN_RATE_LIMIT_STRESS" == "true" ]]; then
  rl_429=0
  rl_retry_after=""
  for i in $(seq 1 340); do
    headers_file="$TMP_DIR/rl_headers_$i.txt"
    code="$(curl -s -D "$headers_file" -o "$TMP_DIR/rl_body_$i.txt" -w '%{http_code}' "$API_BASE_URL/api/assistant/health")"
    if [[ "$code" == "429" ]]; then
      rl_429=1
      rl_retry_after="$(grep -i '^Retry-After:' "$headers_file" | head -n1 | awk '{print $2}' | tr -d '\r')"
      break
    fi
  done

  if [[ "$rl_429" == "1" ]] && [[ -n "$rl_retry_after" ]]; then
    record "Gateway rate-limit behavior (429 + Retry-After)" true "retryAfter=$rl_retry_after" "429"
  else
    record "Gateway rate-limit behavior (429 + Retry-After)" false "did not observe 429 within stress window" "200"
  fi

  CODE="$(curl -s -o "$TMP_DIR/obs_admin_after_rl.json" -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" "$API_BASE_URL/api/admin/observability/summary")"
  if [[ "$CODE" == "200" ]] && jq -e '.security.rateLimitRejectedTotal|type=="number"' "$TMP_DIR/obs_admin_after_rl.json" >/dev/null; then
    rl_total="$(jq -r '.security.rateLimitRejectedTotal' "$TMP_DIR/obs_admin_after_rl.json")"
    audit_total="$(jq -r '.audit.totalEvents' "$TMP_DIR/obs_admin_after_rl.json")"
    if [[ "$rl_total" != "0" && "$audit_total" != "0" ]]; then
      record "Observability summary reflects rate-limit + audit counters" true "rateLimitRejectedTotal=$rl_total audit.totalEvents=$audit_total" "$CODE"
    else
      record "Observability summary reflects rate-limit + audit counters" false "rateLimitRejectedTotal=$rl_total audit.totalEvents=$audit_total" "$CODE"
    fi
  else
    record "Observability summary reflects rate-limit + audit counters" false "body=$(cat "$TMP_DIR/obs_admin_after_rl.json")" "$CODE"
  fi

  CODE="$(curl -s -o "$TMP_DIR/act_prom_after_rl.txt" -w '%{http_code}' "$API_BASE_URL/actuator/prometheus")"
  if [[ "$CODE" == "200" ]] \
    && grep -q 'kc_security_rate_limit_rejections_total' "$TMP_DIR/act_prom_after_rl.txt" \
    && grep -q 'kc_audit_events_total' "$TMP_DIR/act_prom_after_rl.txt"; then
    record "Prometheus includes rate-limit + audit metrics" true "found kc_security_rate_limit_rejections_total and kc_audit_events_total" "$CODE"
  else
    record "Prometheus includes rate-limit + audit metrics" false "missing expected rate-limit/audit metrics" "$CODE"
  fi
fi

jq -n \
  --argjson pass "$PASS" \
  --argjson fail "$FAIL" \
  --slurpfile checks "$REPORT_FILE" \
  '{pass:$pass,fail:$fail,total:($pass+$fail),checks:$checks[0]}'

if [[ "$FAIL" -gt 0 ]]; then
  exit 1
fi

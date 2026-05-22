#!/usr/bin/env bash
set -euo pipefail

# Seeds deterministic demo data for functional + non-functional requirement walkthroughs.
#
# What it does:
# 1) Creates structured sample files in knowledge-base workspace folders
# 2) Triggers admin reindex
# 3) Applies metadata and ACL tags in PostgreSQL
# 4) Generates representative ask/feedback/evaluation traffic
# 5) Optionally triggers rate-limit events for observability proofs
#
# Required services:
# - App API
# - Keycloak
# - PostgreSQL
#
# Environment overrides:
#   API_BASE_URL=http://localhost:8080
#   KEYCLOAK_BASE_URL=http://localhost:8081
#   KEYCLOAK_REALM=knowledge-copilot
#   KEYCLOAK_CLIENT_ID=knowledge-copilot-api
#   USER_USERNAME=kc_user
#   USER_PASSWORD=user123
#   ADMIN_USERNAME=kc_admin
#   ADMIN_PASSWORD=admin123
#   DATABASE_URL=postgresql://postgres:postgres@localhost:5432/knowledge_copilot
#   KNOWLEDGE_BASE_DIR=./knowledge-base
#   RUN_RATE_LIMIT_STRESS=true
#   CHROMA_BASE_URL=http://localhost:8000
#   APP_HEALTH_TIMEOUT_SECONDS=180

for dep in curl jq psql; do
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
DATABASE_URL="${DATABASE_URL:-postgresql://postgres:postgres@localhost:5432/knowledge_copilot}"
KNOWLEDGE_BASE_DIR="${KNOWLEDGE_BASE_DIR:-./knowledge-base}"
RUN_RATE_LIMIT_STRESS="${RUN_RATE_LIMIT_STRESS:-true}"
CHROMA_BASE_URL="${CHROMA_BASE_URL:-http://localhost:8000}"
APP_HEALTH_TIMEOUT_SECONDS="${APP_HEALTH_TIMEOUT_SECONDS:-180}"

log() {
  echo "[seed-demo-data] $*"
}

fail() {
  echo "[seed-demo-data] ERROR: $*" >&2
  exit 1
}

wait_for_app_health() {
  local deadline=$((SECONDS + APP_HEALTH_TIMEOUT_SECONDS))
  local health_code=""
  while [[ "$SECONDS" -lt "$deadline" ]]; do
    health_code="$(curl -s -o /tmp/kc_seed_health.txt -w '%{http_code}' "$API_BASE_URL/api/assistant/health" || true)"
    if [[ "$health_code" == "200" ]]; then
      return 0
    fi
    sleep 2
  done
  fail "App health check failed at $API_BASE_URL/api/assistant/health (status=$health_code) after ${APP_HEALTH_TIMEOUT_SECONDS}s. If running Docker, check: docker compose ps && docker compose logs --tail=100 app"
}

wait_for_app_health

if [[ -f .env ]] && grep -Eq '^CHROMA_API_VERSION=v1$' .env; then
  log "Detected .env uses CHROMA_API_VERSION=v1; for chromadb/chroma:1.x you should use CHROMA_API_VERSION=v2."
fi

chroma_v2_code="$(curl -s -o /tmp/kc_seed_chroma_v2.txt -w '%{http_code}' "$CHROMA_BASE_URL/api/v2/heartbeat" || true)"
if [[ "$chroma_v2_code" != "200" ]]; then
  chroma_v1_code="$(curl -s -o /tmp/kc_seed_chroma_v1.txt -w '%{http_code}' "$CHROMA_BASE_URL/api/v1/heartbeat" || true)"
  fail "Chroma heartbeat failed at $CHROMA_BASE_URL (v2=$chroma_v2_code, v1=$chroma_v1_code). Verify CHROMA_BASE_URL and Chroma container."
fi

mkdir -p \
  "$KNOWLEDGE_BASE_DIR/workspace-ops" \
  "$KNOWLEDGE_BASE_DIR/workspace-security" \
  "$KNOWLEDGE_BASE_DIR/workspace-admin" \
  "$KNOWLEDGE_BASE_DIR/workspace-qa" \
  "$KNOWLEDGE_BASE_DIR/workspace-platform"

cat > "$KNOWLEDGE_BASE_DIR/workspace-ops/ingestion-runbook.md" <<'EOF'
# Ingestion Runbook

## Purpose
This runbook describes the deterministic ingestion flow in Knowledge Copilot.

## Flow
1. Connector full sync lists source items.
2. Text extraction runs for supported files.
3. Token-aware chunking splits content with overlap.
4. Embeddings are generated for each chunk.
5. Chunks are indexed in ChromaDB and metadata persisted in PostgreSQL.
6. Reindex pruning removes stale documents no longer present in the connector source.

## Operational Notes
- Admin reindex endpoint: POST /api/admin/reindex
- Scheduled reindex trigger runs every 15 minutes after initial delay.
- Failed documents are counted in ingestion metrics and logged with stack traces.
EOF

cat > "$KNOWLEDGE_BASE_DIR/workspace-ops/retrieval-filters-reference.md" <<'EOF'
# Retrieval Filters Reference

The ask endpoint supports deterministic filtering:

- sourceTypes: LOCAL_FILE_SYSTEM, JIRA, CONFLUENCE
- sourcePaths: include only source path prefixes
- workspaceIds: match workspace metadata or source-path-derived workspace
- updatedAfter / updatedBefore: inclusive timestamp filtering
- metadataFilters: exact key-value matching on document/chunk metadata
- connectorFilters: connector/source-path specific constraints

Common example:
Use workspaceIds=["workspace-ops"] with sourceTypes=["LOCAL_FILE_SYSTEM"] for operations docs only.
EOF

cat > "$KNOWLEDGE_BASE_DIR/workspace-security/acl-policy.md" <<'EOF'
# Authorization-Aware RAG Policy

## Mandatory Rule
Never send unauthorized chunks to the LLM context.

## Correct Pipeline
Retrieve candidates -> apply ACL filter -> rerank authorized chunks -> build prompt context.

## Expected Behavior
- ROLE_ADMIN can access all content.
- ROLE_USER receives only chunks allowed by access metadata.
- Unauthorized traces may appear in diagnostics with authorized=false and excerpt redacted.
EOF

cat > "$KNOWLEDGE_BASE_DIR/workspace-admin/confidential-incident-2026q1.md" <<'EOF'
# Confidential Incident Review 2026 Q1

Classification: Confidential
Audience: Admin only

Summary:
- Incident ID: KC-INC-2026-017
- Root cause: stale JWK endpoint override during host-mode app execution.
- Blast radius: authorization failures on protected routes.
- Corrective action: enforce localhost JWK endpoint in host profile and add startup validation.
- Follow-up: add explicit environment compatibility checks to deployment checklist.
EOF

cat > "$KNOWLEDGE_BASE_DIR/workspace-qa/evaluation-playbook.md" <<'EOF'
# Evaluation Playbook

Golden questions should represent core business workflows and policy retrieval.

Each evaluation run should store:
- expected sources
- retrieved chunk traces
- citations
- generated answer
- latency
- model used
- prompt version
- feedback aggregates by question cluster
EOF

cat > "$KNOWLEDGE_BASE_DIR/workspace-platform/observability-slos.txt" <<'EOF'
Observability SLOs
- 95th percentile ask latency under 2500ms for steady-state local dataset queries.
- Reindex success count should increase after admin reindex calls.
- Security metrics must include auth failures and rate-limit rejections.
- Audit events must be persisted for assistant ask, feedback, admin reindex, and evaluation actions.
EOF

# Deterministic updatedAt spread for date-filter demos.
touch -t 202401030900 "$KNOWLEDGE_BASE_DIR/workspace-ops/ingestion-runbook.md"
touch -t 202402170930 "$KNOWLEDGE_BASE_DIR/workspace-ops/retrieval-filters-reference.md"
touch -t 202403211000 "$KNOWLEDGE_BASE_DIR/workspace-security/acl-policy.md"
touch -t 202404250945 "$KNOWLEDGE_BASE_DIR/workspace-admin/confidential-incident-2026q1.md"
touch -t 202405061130 "$KNOWLEDGE_BASE_DIR/workspace-qa/evaluation-playbook.md"
touch -t 202405191020 "$KNOWLEDGE_BASE_DIR/workspace-platform/observability-slos.txt"

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
[[ -n "$USER_TOKEN" ]] || fail "Unable to fetch user token from Keycloak."
[[ -n "$ADMIN_TOKEN" ]] || fail "Unable to fetch admin token from Keycloak."

log "Triggering admin reindex..."
reindex_code="$(curl -s -o /tmp/kc_seed_reindex.txt -w '%{http_code}' -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$API_BASE_URL/api/admin/reindex")"
if [[ "$reindex_code" != "202" ]]; then
  fail "Admin reindex failed (status=$reindex_code body=$(cat /tmp/kc_seed_reindex.txt))"
fi

seed_doc_count="$(psql "$DATABASE_URL" -Atc "
SELECT COUNT(*)
FROM knowledge_document
WHERE source_path LIKE '%/workspace-%/%';
")"
if [[ "${seed_doc_count:-0}" -lt 6 ]]; then
  fail "Expected at least 6 workspace seed documents, found: ${seed_doc_count:-0}. If app logs contain '/v1 API is deprecated', set CHROMA_API_VERSION=v2 and restart the app."
fi

log "Applying metadata and ACL tags in PostgreSQL..."
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 <<'SQL'
UPDATE knowledge_document
SET metadata_json = CASE
  WHEN source_path LIKE '%/workspace-ops/%' THEN '{"workspaceId":"workspace-ops","department":"operations","classification":"public","topic":"ingestion"}'
  WHEN source_path LIKE '%/workspace-security/%' THEN '{"workspaceId":"workspace-security","department":"security","classification":"internal","topic":"authorization"}'
  WHEN source_path LIKE '%/workspace-admin/%' THEN '{"workspaceId":"workspace-admin","department":"admin","classification":"confidential","topic":"incident-review"}'
  WHEN source_path LIKE '%/workspace-qa/%' THEN '{"workspaceId":"workspace-qa","department":"quality","classification":"internal","topic":"evaluation"}'
  WHEN source_path LIKE '%/workspace-platform/%' THEN '{"workspaceId":"workspace-platform","department":"platform","classification":"public","topic":"observability"}'
  ELSE metadata_json
END
WHERE source_path LIKE '%/workspace-%/%';

UPDATE knowledge_chunk kc
SET metadata_json = CASE
  WHEN kd.source_path LIKE '%/workspace-admin/%'
    THEN '{"access":{"allowedRoles":["ADMIN"],"allowedUsers":["kc_admin"]},"classification":"confidential","department":"admin"}'
  WHEN kd.source_path LIKE '%/workspace-security/%'
    THEN '{"access":{"allowedRoles":["USER","ADMIN"]},"classification":"internal","department":"security"}'
  ELSE '{"access":{"public":true}}'
END
FROM knowledge_document kd
WHERE kc.document_id = kd.id
  AND kd.source_path LIKE '%/workspace-%/%';
SQL

ask() {
  local token="$1"
  local payload="$2"
  local out_file="$3"
  local code
  code="$(curl -s -o "$out_file" -w '%{http_code}' \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "$API_BASE_URL/api/assistant/ask")"
  echo "$code"
}

submit_feedback() {
  local token="$1"
  local payload="$2"
  local out_file="$3"
  curl -s -o "$out_file" -w '%{http_code}' \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d "$payload" \
    "$API_BASE_URL/api/assistant/feedback"
}

log "Generating assistant traffic (ask + feedback)..."
code_user_ops="$(ask "$USER_TOKEN" '{"question":"Summarize the ingestion runbook steps in order.","workspaceIds":["workspace-ops"],"sourceTypes":["LOCAL_FILE_SYSTEM"]}' /tmp/kc_seed_ask_user_ops.json)"
[[ "$code_user_ops" == "200" ]] || fail "User ops ask failed (status=$code_user_ops body=$(cat /tmp/kc_seed_ask_user_ops.json))"
rid_user_ops="$(jq -r '.responseId // empty' /tmp/kc_seed_ask_user_ops.json)"
[[ -n "$rid_user_ops" ]] || fail "Missing responseId for user ops ask."

code_user_conf="$(ask "$USER_TOKEN" '{"question":"What was the root cause in confidential incident KC-INC-2026-017?","workspaceIds":["workspace-admin"]}' /tmp/kc_seed_ask_user_conf.json)"
[[ "$code_user_conf" == "200" ]] || fail "User confidential ask failed (status=$code_user_conf)"

code_admin_conf="$(ask "$ADMIN_TOKEN" '{"question":"What was the root cause in confidential incident KC-INC-2026-017?","workspaceIds":["workspace-admin"]}' /tmp/kc_seed_ask_admin_conf.json)"
[[ "$code_admin_conf" == "200" ]] || fail "Admin confidential ask failed (status=$code_admin_conf body=$(cat /tmp/kc_seed_ask_admin_conf.json))"
rid_admin_conf="$(jq -r '.responseId // empty' /tmp/kc_seed_ask_admin_conf.json)"
[[ -n "$rid_admin_conf" ]] || fail "Missing responseId for admin confidential ask."

code_user_meta="$(ask "$USER_TOKEN" '{"question":"What does authorization-aware RAG require?","metadataFilters":{"department":"security"}}' /tmp/kc_seed_ask_user_meta.json)"
[[ "$code_user_meta" == "200" ]] || fail "Metadata filter ask failed (status=$code_user_meta)"

fb_code_1="$(submit_feedback "$USER_TOKEN" "{\"responseId\":\"$rid_user_ops\",\"verdict\":\"GOOD\",\"comment\":\"clear runbook answer\"}" /tmp/kc_seed_fb_user.json)"
[[ "$fb_code_1" == "200" ]] || fail "User feedback submit failed (status=$fb_code_1 body=$(cat /tmp/kc_seed_fb_user.json))"

fb_code_2="$(submit_feedback "$ADMIN_TOKEN" "{\"responseId\":\"$rid_admin_conf\",\"verdict\":\"GOOD\",\"comment\":\"matches incident notes\"}" /tmp/kc_seed_fb_admin.json)"
[[ "$fb_code_2" == "200" ]] || fail "Admin feedback submit failed (status=$fb_code_2 body=$(cat /tmp/kc_seed_fb_admin.json))"

ops_path="$(psql "$DATABASE_URL" -Atc "SELECT source_path FROM knowledge_document WHERE file_name='ingestion-runbook.md' ORDER BY indexed_at DESC LIMIT 1;")"
sec_path="$(psql "$DATABASE_URL" -Atc "SELECT source_path FROM knowledge_document WHERE file_name='acl-policy.md' ORDER BY indexed_at DESC LIMIT 1;")"
[[ -n "$ops_path" && -n "$sec_path" ]] || fail "Could not resolve expected source paths for golden questions."

log "Creating evaluation signals..."
golden_payload="$(jq -cn --arg p1 "$ops_path" --arg p2 "$sec_path" '{question:"How does authorization-aware ingestion and retrieval work?", expectedSources:[$p1,$p2]}')"
golden_code="$(curl -s -o /tmp/kc_seed_golden.json -w '%{http_code}' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d "$golden_payload" \
  "$API_BASE_URL/api/admin/evaluations/golden-questions")"
[[ "$golden_code" == "201" ]] || fail "Golden question create failed (status=$golden_code body=$(cat /tmp/kc_seed_golden.json))"
golden_id="$(jq -r '.id // empty' /tmp/kc_seed_golden.json)"
[[ -n "$golden_id" ]] || fail "Missing golden question id."

run_code="$(curl -s -o /tmp/kc_seed_run.json -w '%{http_code}' \
  -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$API_BASE_URL/api/admin/evaluations/runs/$golden_id")"
[[ "$run_code" == "201" ]] || fail "Evaluation run failed (status=$run_code body=$(cat /tmp/kc_seed_run.json))"
run_id="$(jq -r '.id // empty' /tmp/kc_seed_run.json)"
[[ -n "$run_id" ]] || fail "Missing evaluation run id."

eval_fb_code="$(curl -s -o /tmp/kc_seed_eval_feedback.json -w '%{http_code}' \
  -X POST \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"score":5,"comment":"seeded run aligns with expected sources"}' \
  "$API_BASE_URL/api/admin/evaluations/runs/$run_id/feedback")"
[[ "$eval_fb_code" == "200" ]] || fail "Evaluation feedback failed (status=$eval_fb_code body=$(cat /tmp/kc_seed_eval_feedback.json))"

if [[ "$RUN_RATE_LIMIT_STRESS" == "true" ]]; then
  log "Generating rate-limit telemetry..."
  saw_429=0
  for i in $(seq 1 340); do
    code="$(curl -s -o /dev/null -w '%{http_code}' "$API_BASE_URL/api/assistant/health")"
    if [[ "$code" == "429" ]]; then
      saw_429=1
      break
    fi
  done
  if [[ "$saw_429" != "1" ]]; then
    log "Rate-limit 429 not observed in stress window; continuing."
  fi
fi

summary_code="$(curl -s -o /tmp/kc_seed_summary.json -w '%{http_code}' \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  "$API_BASE_URL/api/admin/observability/summary")"
[[ "$summary_code" == "200" ]] || fail "Observability summary fetch failed (status=$summary_code body=$(cat /tmp/kc_seed_summary.json))"

log "Seed complete."
jq '{
  seededWorkspaceDocuments: .inventory.documentsIndexed,
  assistantResponses: .inventory.assistantResponses,
  evaluationRuns: .inventory.evaluationRuns,
  feedbackGood: .inventory.feedbackGood,
  feedbackBad: .inventory.feedbackBad,
  askSuccessTotal: .ask.totalSuccess,
  reindexSuccessTotal: .ingestion.reindexSuccessTotal,
  rateLimitRejectedTotal: .security.rateLimitRejectedTotal,
  auditEventsTotal: .audit.totalEvents
}' /tmp/kc_seed_summary.json

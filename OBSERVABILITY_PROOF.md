# Observability Layer Proof

This document validates the six high-value observability additions end-to-end:

1. Request/API metrics  
2. RAG quality metrics  
3. LLM + embedding/OpenAI metrics  
4. Ingestion/reindex metrics  
5. Correlation IDs + traces  
6. Structured logs + dashboards
7. Gateway rate-limit and audit-event telemetry

## 1) Start stack

```bash
docker compose up --build -d
```

UI/monitoring endpoints:
- App: `http://localhost:8080`
- ChromaDB: `http://localhost:8000`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Jaeger: `http://localhost:16686`

ChromaDB health check:
```bash
curl --location 'http://localhost:8000/api/v2/heartbeat'
```

## 2) Get tokens

User token:
```bash
curl --location 'http://localhost:8081/realms/knowledge-copilot/protocol/openid-connect/token' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'client_id=knowledge-copilot-api' \
--data-urlencode 'grant_type=password' \
--data-urlencode 'username=kc_user' \
--data-urlencode 'password=user123'
```

Admin token:
```bash
curl --location 'http://localhost:8081/realms/knowledge-copilot/protocol/openid-connect/token' \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data-urlencode 'client_id=knowledge-copilot-api' \
--data-urlencode 'grant_type=password' \
--data-urlencode 'username=kc_admin' \
--data-urlencode 'password=admin123'
```

## 3) Generate traffic that should move metrics

Call ask with explicit correlation ID:
```bash
curl --location 'http://localhost:8080/api/assistant/ask' \
--header 'Authorization: Bearer <user_access_token>' \
--header 'X-Request-Id: obs-proof-ask-1' \
--header 'Content-Type: application/json' \
--data '{"question":"How does ingestion flow work?"}'
```
Expected payload includes `responseId` and boolean `insufficientEvidence`.

Optional quick check:
```bash
curl --location 'http://localhost:8080/api/assistant/ask' \
--header 'Authorization: Bearer <user_access_token>' \
--header 'Content-Type: application/json' \
--data '{"question":"Can you summarize the previous answer?"}' | jq '{responseId,insufficientEvidence}'
```
The second request uses per-user conversation summary from recent turns.

Trigger one insufficient-evidence event (so insufficient counter appears):
```bash
curl --location 'http://localhost:8080/api/assistant/ask' \
--header 'Authorization: Bearer <user_access_token>' \
--header 'Content-Type: application/json' \
--data '{"question":"What is the SU(3) gauge lagrangian derivation from this knowledge base?"}' | jq '{responseId,insufficientEvidence}'
```

Submit feedback (`GOOD` then `BAD` to test flip/update):
```bash
curl --location --request POST 'http://localhost:8080/api/assistant/feedback' \
--header 'Authorization: Bearer <user_access_token>' \
--header 'Content-Type: application/json' \
--data '{"responseId":"<response_id>","verdict":"GOOD","comment":"accurate"}'

curl --location --request POST 'http://localhost:8080/api/assistant/feedback' \
--header 'Authorization: Bearer <user_access_token>' \
--header 'Content-Type: application/json' \
--data '{"responseId":"<response_id>","verdict":"BAD","comment":"needs more detail"}'
```

Trigger ingestion:
```bash
curl --location --request POST 'http://localhost:8080/api/admin/reindex' \
--header 'Authorization: Bearer <admin_access_token>'
```

Run one evaluation:
```bash
curl --location 'http://localhost:8080/api/admin/evaluations/golden-questions' \
--header 'Authorization: Bearer <admin_access_token>' \
--header 'Content-Type: application/json' \
--data '{"question":"How does ingestion flow work?","expectedSources":["/app/knowledge-base/ingestion.md"]}'

curl --location --request POST 'http://localhost:8080/api/admin/evaluations/runs/<golden_question_id>' \
--header 'Authorization: Bearer <admin_access_token>'
```
Use the `id` from this response as `<evaluation_run_id>`.

Save numeric evaluation feedback score:
```bash
curl --location --request POST 'http://localhost:8080/api/admin/evaluations/runs/<evaluation_run_id>/feedback' \
--header 'Authorization: Bearer <admin_access_token>' \
--header 'Content-Type: application/json' \
--data '{"score":4,"comment":"Useful but can be more detailed"}'
```

## 4) Validate Prometheus metrics values

```bash
curl --location 'http://localhost:8080/actuator/prometheus' | grep '^kc_'
```

Key metrics to check:
- API/request: `kc_ask_requests_total`, `kc_ask_latency_*`
- Retrieval quality: `kc_retrieval_chunks_*`, `kc_retrieval_authorized_chunks_*`, `kc_retrieval_citations_*`
- Evaluation quality: `kc_evaluation_runs_total`, `kc_evaluation_citation_correctness_*`, `kc_evaluation_matched_sources_*`
- Feedback: `kc_feedback_submissions_total`, `kc_feedback_good_rate_*`, `kc_feedback_score_*`
- OpenAI: `kc_openai_llm_requests_total`, `kc_openai_llm_latency_*`, `kc_openai_embedding_requests_total`
- Ingestion: `kc_ingestion_reindex_total`, `kc_ingestion_reindex_latency_*`, `kc_ingestion_connector_sync_latency_*`, `kc_ingestion_document_latency_*`, `kc_ingestion_chunks_total_*`
- Security failures: `kc_security_auth_failures_total`
- Security rate-limit: `kc_security_rate_limit_rejections_total`
- Audit event counter: `kc_audit_events_total`
- Insufficient-answer counter: `kc_ask_insufficient_answers_total` (appears after at least one insufficient event)

For hybrid retrieval stage visibility, validate `kc_retrieval_chunks_*` by `stage` tag:
- `vector_candidates`
- `keyword_candidates`
- `filtered_candidates`
- `top_k`

PromQL checks for newly added metrics:
```promql
# Connector sync duration (avg over 5m), grouped by connector
rate(kc_ingestion_connector_sync_latency_sum[5m])
/
clamp_min(rate(kc_ingestion_connector_sync_latency_count[5m]), 1)

# Insufficient-answer rate over successful asks (5m)
sum(rate(kc_ask_insufficient_answers_total[5m]))
/
clamp_min(sum(rate(kc_ask_requests_total{status="success"}[5m])), 1)

# Feedback score average (5m)
rate(kc_feedback_score_sum[5m])
/
clamp_min(rate(kc_feedback_score_count[5m]), 1)
```

ACL redaction proof (authorization-aware trace hygiene):
1. Mark one chunk as admin-only ACL in DB.
2. Ask same question as `ROLE_USER`.
3. Inspect `assistant_response_record.retrieved_chunks_json` for that response.
4. Verify unauthorized entries have `authorized=false` and `excerpt=""`.

## 5) Validate admin summary endpoint

```bash
curl --location 'http://localhost:8080/api/admin/observability/summary' \
--header 'Authorization: Bearer <admin_access_token>'
```

Check consistency:
- `inventory.assistantResponses` increases after `/ask`
- `inventory.feedbackGood` or `inventory.feedbackBad` reflects submitted feedback
- `ask.totalSuccess` increases after successful asks
- `evaluation.avgCitationCorrectnessRecent200` is between `0` and `1`
- `ingestion.reindexSuccessTotal` increases after admin reindex

## 6) Validate correlation + traces + logs

Confirm response includes same request ID:
```bash
curl -i --location 'http://localhost:8080/api/assistant/ask' \
--header 'Authorization: Bearer <user_access_token>' \
--header 'X-Request-Id: obs-proof-ask-2' \
--header 'Content-Type: application/json' \
--data '{"question":"What is reindex schedule?"}'
```

Inspect app logs:
```bash
docker logs knowledge-copilot-app --tail 50
```

Expected in JSON logs:
- `requestId` (from `X-Request-Id`)
- `traceId` and `spanId` (Micrometer tracing)
- structured fields (`timestamp`, `level`, `logger`, `message`, `service`)

Open Jaeger UI (`http://localhost:16686`) and search service `knowledge-copilot` to confirm traces are exported.

## 7) Validate gateway rate-limit and audit persistence

Trigger rate-limit on a lightweight endpoint:
```bash
for i in $(seq 1 340); do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/assistant/health
done
```
Expected: responses eventually include `429`.

Check metric:
```bash
curl -s http://localhost:8080/actuator/prometheus | grep kc_security_rate_limit_rejections_total
```

Check audit records:
```bash
psql "postgresql://postgres:postgres@localhost:5432/knowledge_copilot" -Atc "
SELECT action, COUNT(*)
FROM audit_event
WHERE created_at > now() - interval '15 minutes'
GROUP BY action
ORDER BY action;"
```
Expected: rows for actions like `ASSISTANT_ASK`, `ASSISTANT_FEEDBACK`, `ADMIN_REINDEX`, and evaluation/admin actions.

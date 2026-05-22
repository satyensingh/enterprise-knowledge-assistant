# Knowledge Copilot MVP

Initial Java Spring Boot MVP for a folder-based enterprise knowledge assistant.

## Features
- Scan local folder for documents
- Ingestion connector framework (`SourceConnector`) with local-file, Jira, and Confluence connectors
- Extract text from TXT / MD / PDF / DOCX
- Token-aware chunking with metadata in PostgreSQL and vector index in ChromaDB
- Hybrid retrieval (vector + keyword) with query understanding, pre-LLM filtering, ACL gating, and reranking
- Scheduled reindexing every 15 minutes after a 1 minute initial delay
- Removed source files are pruned from the index during reindexing
- Ask API with rich citations
- Ask API returns machine-readable `insufficientEvidence` and uses per-user conversation summary for better follow-up handling
- Authorization-aware RAG: candidate retrieval -> ACL filter -> only authorized chunks passed to LLM; unauthorized retrieved traces are redacted (`excerpt=""`)
- Retrieval filters in `/api/assistant/ask`: `sourceTypes`, `sourcePaths`, `workspaceIds`, `updatedAfter`, `updatedBefore`, `metadataFilters`, `connectorFilters`
- Keycloak JWT security with seeded `ROLE_USER` and `ROLE_ADMIN` users
- Admin reindex API (`POST /api/admin/reindex`)
- Gateway rate-limiting hooks for `/api/**` with configurable per-window limits
- Gateway audit event creation persisted in `audit_event` for assistant/admin/evaluation flows
- Evaluation layer for RAG quality tracking (golden questions, expected sources, retrieved chunks, generated answer, citation correctness, user feedback, latency, model, prompt version)
- Observability layer:
  - Prometheus metrics (`/actuator/prometheus`)
  - Request correlation (`X-Request-Id`) + trace IDs in logs
  - Structured JSON logs
  - Admin observability summary (`GET /api/admin/observability/summary`)
  - Grafana dashboard + Jaeger tracing in Docker compose

## Run
1. Create PostgreSQL database:
   ```sql
   CREATE DATABASE knowledge_copilot;
   ```
2. Start supporting containers:
   ```bash
   docker compose up keycloak postgres chromadb -d
   ```
3. Set `OPENAI_API_KEY`.
   Optional Chroma overrides:
   ```bash
   export CHROMA_BASE_URL="http://localhost:8000"
   export CHROMA_COLLECTION_NAME="knowledge_chunks"
   export CHROMA_API_VERSION="v2"
   ```
4. Update `src/main/resources/application.yml` if needed.
   Optional Chroma health check:
   ```bash
   curl --location 'http://localhost:8000/api/v2/heartbeat'
   ```
5. Create a `knowledge-base` folder and place sample files there.
6. Start the app:
   ```bash
   mvn spring-boot:run
   ```
7. Get a Keycloak access token (`ROLE_USER`):
   ```bash
   curl --location 'http://localhost:8081/realms/knowledge-copilot/protocol/openid-connect/token' \
   --header 'Content-Type: application/x-www-form-urlencoded' \
   --data-urlencode 'client_id=knowledge-copilot-api' \
   --data-urlencode 'grant_type=password' \
   --data-urlencode 'username=kc_user' \
   --data-urlencode 'password=user123'
   ```
8. Ask a question (use the token from step 7):
   ```bash
   curl --location 'http://localhost:8080/api/assistant/ask' \
   --header 'Authorization: Bearer <access_token>' \
   --header 'Content-Type: application/json' \
   --data '{"question":"How does the ingestion flow work?"}'
   ```
   The response now includes:
   - `responseId` (used for user feedback)
   - `insufficientEvidence` (`true/false` reliability signal)

9. Ask with retrieval filters:
   ```bash
   curl --location 'http://localhost:8080/api/assistant/ask' \
   --header 'Authorization: Bearer <access_token>' \
   --header 'Content-Type: application/json' \
   --data '{
     "question":"How does ingestion flow work?",
     "sourceTypes":["LOCAL_FILE_SYSTEM"],
     "workspaceIds":["knowledge-base"],
     "connectorFilters":{"sourcePathContains":"knowledge-base"}
   }'
   ```

Use the seeded admin user (`kc_admin` / `admin123`) to call administrative routes:
```bash
curl --location --request POST 'http://localhost:8080/api/admin/reindex' \
--header 'Authorization: Bearer <admin_access_token>'
```
Admin summary endpoint:
```bash
curl --location 'http://localhost:8080/api/admin/observability/summary' \
--header 'Authorization: Bearer <admin_access_token>'
```

### Swagger / OpenAPI
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

Most endpoints require JWT bearer auth. In Swagger UI:
1. Click `Authorize`.
2. Paste `Bearer <access_token>` using token from Keycloak.

The security layer is enabled by default. Set `APP_SECURITY_ENABLED=false` only for local experiments where anonymous access is intentional.

Retrieval authorization also respects chunk metadata when present:
```json
{"access":{"allowedRoles":["USER"],"allowedUsers":["alice"]}}
```
Chunks without ACL metadata remain accessible to authenticated users; admin access bypasses chunk ACL checks.
For strict leakage prevention in diagnostics, unauthorized retrieved chunk traces are kept with `authorized=false` but their `excerpt` is blank.

### Retrieval pipeline
`/api/assistant/ask` now follows this execution order:

1. Query understanding (normalize question + extract keyword terms)
2. Source/workspace/date/metadata/connector filter planning
3. Hybrid retrieval (vector candidates + keyword candidates)
4. Filter application on candidates
5. ACL filtering
6. Reranking on authorized candidates
7. Context assembly (authorized chunks only)
8. LLM answer generation with citation metadata context
9. Citation packaging

This prevents data leakage from unauthorized chunks and keeps retrieval behavior auditable.

### Gateway rate limiting
Rate limiting is enabled by default for `/api/**`.

Defaults per 60-second window:
- `ask`: `120`
- `feedback`: `240`
- `admin`: `90`
- other `/api/**`: `300`

Config environment variables:
```bash
export APP_SECURITY_RATE_LIMIT_ENABLED=true
export APP_SECURITY_RATE_LIMIT_WINDOW_SECONDS=60
export APP_SECURITY_RATE_LIMIT_ASK_LIMIT=120
export APP_SECURITY_RATE_LIMIT_FEEDBACK_LIMIT=240
export APP_SECURITY_RATE_LIMIT_ADMIN_LIMIT=90
export APP_SECURITY_RATE_LIMIT_DEFAULT_LIMIT=300
export APP_SECURITY_RATE_LIMIT_MAX_TRACKED_KEYS=50000
```

When exceeded, API returns HTTP `429` with `Retry-After` header and JSON payload:
`error=rate_limited`.

### Audit events
Audit events are written to `audit_event` with:
- actor
- action
- resource type/id
- request path/method
- status code
- request correlation id
- metadata JSON
- timestamp

Current audited actions include:
- `ASSISTANT_ASK`
- `ASSISTANT_FEEDBACK`
- `ADMIN_REINDEX`
- `ADMIN_OBSERVABILITY_SUMMARY`
- `EVAL_GOLDEN_CREATE`
- `EVAL_GOLDEN_LIST`
- `EVAL_RUN_EXECUTE`
- `EVAL_RUN_LIST`
- `EVAL_RUN_FEEDBACK`

### Enable Jira ingestion
Set these environment variables before starting the app:
```bash
export JIRA_ENABLED=true
export JIRA_BASE_URL="https://your-domain.atlassian.net"
export JIRA_EMAIL="you@company.com"
export JIRA_API_TOKEN="your-jira-api-token"
export JIRA_JQL='project = ABC ORDER BY updated DESC'
```
When enabled, Jira issues are ingested through the same chunk/embed/index pipeline as file sources.

### Enable Confluence ingestion
Set these environment variables before starting the app:
```bash
export CONFLUENCE_ENABLED=true
export CONFLUENCE_BASE_URL="https://your-domain.atlassian.net"
export CONFLUENCE_EMAIL="you@company.com"
export CONFLUENCE_API_TOKEN="your-confluence-api-token"
export CONFLUENCE_CQL='type=page order by lastmodified desc'
```
When enabled, Confluence pages are ingested through the same chunk/embed/index pipeline as other sources.

### Citations
Each citation includes:
- `documentId`
- `fileName`
- `title`
- `sourcePath`
- `sourceType`
- `contentType`
- `checksum`
- `indexedAt`
- `updatedAt`
- `chunkIndex`
- `citationLabel`
- `excerpt`

These fields make it easier to audit answers, detect stale sources, and inspect the exact chunk used by the retriever.

## Next improvements
- Add page/section anchors for PDF and DOCX citations

## FRD Deviations (Current Scope)
The current build is intentionally scoped for MVP delivery and does not implement every FRD connector item yet.

Implemented now:
- Connector contract methods: `testConnection()`, `fullSync()`, `incrementalSync()`, `fetchItem()`, `fetchPermissions()`, `markDeleted()`
- First-class connectors: Local/shared folder, Jira, Confluence

Deferred to Phase 2:
1. Add explicit `normalize()` method to `SourceConnector` (normalization is currently done inside each connector via `NormalizedSourceItem` mapping).
2. Orchestrate production incremental sync strategy (`incrementalSync`) in scheduled/admin flows (current default is full sync per reindex cycle).
3. Add Slack connector.
4. Add Microsoft Teams connector.
5. Add Direct Upload connector/API.

Rationale:
- Keeps MVP complexity and operational risk controlled.
- Preserves a stable foundation for security, evaluation, and observability layers already implemented.
- Defers high-effort integrations until connector-specific requirements and access patterns are finalized.


## Run with Docker
1. Create a local folder named `knowledge-base` in the project root and put your sample documents there.
2. Export your OpenAI API key in your shell:
   ```bash
   export OPENAI_API_KEY="your-key-here"
   ```
   Optional Keycloak overrides:
   ```bash
   export KEYCLOAK_ISSUER_URI="http://localhost:8081/realms/knowledge-copilot"
   export KEYCLOAK_JWK_SET_URI="http://keycloak:8080/realms/knowledge-copilot/protocol/openid-connect/certs"
   export KEYCLOAK_ADMIN_USERNAME="admin"
   export KEYCLOAK_ADMIN_PASSWORD="admin"
   export KEYCLOAK_HOSTNAME="http://localhost:8081"
   ```
   Optional embedding overrides:
   ```bash
   export OPENAI_EMBEDDING_MODEL="text-embedding-3-small"
   export OPENAI_EMBEDDING_DIMENSIONS="1536"
   export OPENAI_PROMPT_VERSION="v1"
   export CHROMA_BASE_URL="http://localhost:8000"
   export CHROMA_COLLECTION_NAME="knowledge_chunks"
   export CHROMA_API_VERSION="v2"
   export EVAL_QUESTION_CLUSTER_CANDIDATE_POOL_SIZE="20"
   export EVAL_QUESTION_CLUSTER_SIMILARITY_THRESHOLD="0.86"
   ```
3. Start the full stack (app + Postgres + Chroma + observability):
   ```bash
   docker compose up --build
   ```
   Optional monitors started by default in the same compose stack:
   - Prometheus: `http://localhost:9090`
   - Grafana: `http://localhost:3000`
   - Jaeger: `http://localhost:16686`
4. PostgreSQL will automatically execute `docker/postgres/01-schema.sql` on first startup.
5. The application waits for PostgreSQL healthcheck to pass before starting.
6. Optional Chroma health check:
   ```bash
   curl --location 'http://localhost:8000/api/v2/heartbeat'
   ```
7. Obtain a token:
   ```bash
   curl --location 'http://localhost:8081/realms/knowledge-copilot/protocol/openid-connect/token' \
   --header 'Content-Type: application/x-www-form-urlencoded' \
   --data-urlencode 'client_id=knowledge-copilot-api' \
   --data-urlencode 'grant_type=password' \
   --data-urlencode 'username=kc_user' \
   --data-urlencode 'password=user123'
   ```
8. Ask a question:
   ```bash
   curl --location 'http://localhost:8080/api/assistant/ask' \
   --header 'Authorization: Bearer <access_token>' \
   --header 'Content-Type: application/json' \
   --data '{"question":"How does the ingestion flow work?"}'
   ```
   The response includes `responseId` used by `/api/assistant/feedback` and `insufficientEvidence` (`true/false`).

9. Verify metrics export:
   ```bash
   curl --location 'http://localhost:8080/actuator/prometheus' | grep kc_
   ```

Chunk sizes and overlap are measured in tokens, not characters.

## Seed Demo Data (Functional + Non-Functional)

To seed reproducible demo data that exercises ingestion, retrieval filters, ACL behavior, evaluation, feedback, audit, and observability metrics:

```bash
bash scripts/seed-demo-data.sh
```

The script:
- creates workspace-scoped sample files under `knowledge-base/workspace-*`
- sets deterministic file timestamps for date-filter demos
- triggers admin reindex
- tags document/chunk metadata in Postgres (`workspaceId`, `department`, `classification`)
- applies admin-only ACL metadata on confidential chunks
- generates API traffic (ask, feedback, evaluations, observability summary)
- optionally stresses rate limiting (`RUN_RATE_LIMIT_STRESS=true` by default)

Common overrides:
```bash
API_BASE_URL=http://localhost:8080 \
KEYCLOAK_BASE_URL=http://localhost:8081 \
CHROMA_BASE_URL=http://localhost:8000 \
DATABASE_URL=postgresql://postgres:postgres@localhost:5432/knowledge_copilot \
APP_HEALTH_TIMEOUT_SECONDS=180 \
RUN_RATE_LIMIT_STRESS=true \
bash scripts/seed-demo-data.sh
```

## Evaluation Layer
Admin endpoints (requires `ROLE_ADMIN` token):

1. Create a golden question:
   ```bash
   curl --location 'http://localhost:8080/api/admin/evaluations/golden-questions' \
   --header 'Authorization: Bearer <admin_access_token>' \
   --header 'Content-Type: application/json' \
   --data '{
     "question":"How does ingestion flow work?",
     "expectedSources":["/app/knowledge-base/ingestion.md","/app/knowledge-base/architecture.md"]
   }'
   ```
2. Run one evaluation:
   ```bash
   curl --location --request POST 'http://localhost:8080/api/admin/evaluations/runs/<golden_question_id>' \
   --header 'Authorization: Bearer <admin_access_token>'
   ```
3. List recent evaluation runs:
   ```bash
   curl --location 'http://localhost:8080/api/admin/evaluations/runs' \
   --header 'Authorization: Bearer <admin_access_token>'
   ```
   Evaluation runs include `questionFeedbackGoodCount`, `questionFeedbackBadCount`, and `questionFeedbackGoodRate` derived from user feedback grouped by semantic question cluster (normalization + embedding similarity), so paraphrased questions roll up together.
4. Save user feedback for a run:
   ```bash
   curl --location --request POST 'http://localhost:8080/api/admin/evaluations/runs/<run_id>/feedback' \
   --header 'Authorization: Bearer <admin_access_token>' \
   --header 'Content-Type: application/json' \
   --data '{"score":4,"comment":"Answer is mostly correct, needs more detail on scheduler."}'
   ```

User feedback endpoint (available to `ROLE_USER` and `ROLE_ADMIN`):
```bash
curl --location --request POST 'http://localhost:8080/api/assistant/feedback' \
--header 'Authorization: Bearer <access_token>' \
--header 'Content-Type: application/json' \
--data '{"responseId":"<response_id_from_ask>","verdict":"GOOD","comment":"clear and accurate"}'
```

Detailed reproducible proof guide:
- `EVALUATION_LAYER_PROOF.md`
- `OBSERVABILITY_PROOF.md`

### Notes
- If you change the SQL init script after the database volume already exists, remove the old volume first:
  ```bash
  docker compose down -v
  docker compose up --build
  ```
- If you keep a local PostgreSQL database from the old schema, recreate it so the `vector` column and HNSW index are built cleanly.
- Docker uses `application-docker.yml`.
- In the Docker profile, schema creation is owned by PostgreSQL init script and Hibernate runs with `ddl-auto=validate`.

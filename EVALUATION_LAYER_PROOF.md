# Evaluation Layer Proof Guide

This guide demonstrates, step by step, that the evaluation layer is working and why it adds measurable value.

Current calibration in this environment:

- `EVAL_QUESTION_CLUSTER_SIMILARITY_THRESHOLD=0.82`

## 1) What this proves

After running this flow, you will have evidence for:

1. Golden questions are persisted and executable.
2. Evaluation runs capture: expected sources, retrieved chunks, citations, answer, latency, model, prompt version.
3. User `GOOD/BAD` feedback is persisted per answer.
4. Feedback is aggregated into evaluation runs.
5. Paraphrased questions can roll up via semantic clustering (normalization + vector similarity), not only exact text.
6. Updating user feedback changes later evaluation aggregates (no duplicate inflation for same user+response).
7. `/ask` now includes machine-readable `insufficientEvidence` and uses recent per-user conversation summary in answer generation.
8. Retrieval filters (source/workspace/date/connector) change evaluation inputs deterministically, so run diagnostics remain explainable.

## 2) Data model signals you should inspect

Evaluation layer outputs:

- `citationCorrectness`
- `expectedSourceCount`
- `matchedExpectedSourceCount`
- `retrievedChunks`
- `citations`
- `generatedAnswer`
- `latencyMs`
- `modelUsed`
- `promptVersion`
- `questionFeedbackGoodCount`
- `questionFeedbackBadCount`
- `questionFeedbackGoodRate`

Stored in:

- `golden_question`
- `evaluation_run`
- `assistant_response_record`
- `assistant_response_feedback`
- `question_cluster`

## 3) Reproducible experiment (copy/paste)

Assumption: services are running at `localhost:8080` (app), `localhost:8081` (Keycloak), `localhost:5432` (Postgres), `localhost:8000` (ChromaDB).

### Step 0: verify ChromaDB is reachable

```bash
curl --location 'http://localhost:8000/api/v2/heartbeat'
```

### Step A: get tokens

```bash
USER_TOKEN=$(curl -s --location 'http://localhost:8081/realms/knowledge-copilot/protocol/openid-connect/token' \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'client_id=knowledge-copilot-api' \
  --data-urlencode 'grant_type=password' \
  --data-urlencode 'username=kc_user' \
  --data-urlencode 'password=user123' | jq -r '.access_token')

ADMIN_TOKEN=$(curl -s --location 'http://localhost:8081/realms/knowledge-copilot/protocol/openid-connect/token' \
  --header 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'client_id=knowledge-copilot-api' \
  --data-urlencode 'grant_type=password' \
  --data-urlencode 'username=kc_admin' \
  --data-urlencode 'password=admin123' | jq -r '.access_token')
```

### Step B: ask 3 related questions (all should merge at threshold `0.82`)

```bash
Q1=$(curl -s --location 'http://localhost:8080/api/assistant/ask' \
  --header "Authorization: Bearer $USER_TOKEN" \
  --header 'Content-Type: application/json' \
  --data '{"question":"How does ingestion flow work?"}')

Q2=$(curl -s --location 'http://localhost:8080/api/assistant/ask' \
  --header "Authorization: Bearer $ADMIN_TOKEN" \
  --header 'Content-Type: application/json' \
  --data '{"question":"How does the ingestion flow work?"}')

Q3=$(curl -s --location 'http://localhost:8080/api/assistant/ask' \
  --header "Authorization: Bearer $USER_TOKEN" \
  --header 'Content-Type: application/json' \
  --data '{"question":"Can you explain the ingestion process flow?"}')

RID1=$(echo "$Q1" | jq -r '.responseId')
RID2=$(echo "$Q2" | jq -r '.responseId')
RID3=$(echo "$Q3" | jq -r '.responseId')

echo "$Q1" | jq '{responseId,citationsCount:(.citations|length),hasAnswer:(.answer|length>0),insufficientEvidence}'
echo "$Q2" | jq '{responseId,citationsCount:(.citations|length),hasAnswer:(.answer|length>0),insufficientEvidence}'
echo "$Q3" | jq '{responseId,citationsCount:(.citations|length),hasAnswer:(.answer|length>0),insufficientEvidence}'
```

Proof checkpoint: all three responses must have non-empty `responseId`, `hasAnswer=true`, `citationsCount > 0`, and boolean `insufficientEvidence`.

### Step C: submit feedback

```bash
curl -s --location --request POST 'http://localhost:8080/api/assistant/feedback' \
  --header "Authorization: Bearer $USER_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "{\"responseId\":\"$RID1\",\"verdict\":\"GOOD\",\"comment\":\"accurate\"}" | jq

curl -s --location --request POST 'http://localhost:8080/api/assistant/feedback' \
  --header "Authorization: Bearer $ADMIN_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "{\"responseId\":\"$RID2\",\"verdict\":\"GOOD\",\"comment\":\"clear\"}" | jq

curl -s --location --request POST 'http://localhost:8080/api/assistant/feedback' \
  --header "Authorization: Bearer $USER_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "{\"responseId\":\"$RID3\",\"verdict\":\"BAD\",\"comment\":\"needs depth\"}" | jq
```

Proof checkpoint: each response returns `goodCount`, `badCount`, `goodRate`.

### Step D: create and run golden evaluation

```bash
GOLDEN=$(curl -s --location 'http://localhost:8080/api/admin/evaluations/golden-questions' \
  --header "Authorization: Bearer $ADMIN_TOKEN" \
  --header 'Content-Type: application/json' \
  --data '{"question":"How does ingestion flow work?","expectedSources":[]}')

GID=$(echo "$GOLDEN" | jq -r '.id')

RUN=$(curl -s --location --request POST "http://localhost:8080/api/admin/evaluations/runs/$GID" \
  --header "Authorization: Bearer $ADMIN_TOKEN")

echo "$RUN" | jq '{
  id,goldenQuestionId,citationCorrectness,expectedSourceCount,matchedExpectedSourceCount,
  retrievedChunksCount:(.retrievedChunks|length),
  citationsCount:(.citations|length),
  latencyMs,modelUsed,promptVersion,
  questionFeedbackGoodCount,questionFeedbackBadCount,questionFeedbackGoodRate
}'
```

Proof checkpoint:

- `retrievedChunksCount > 0`
- `citationsCount > 0`
- `latencyMs > 0`
- `modelUsed` and `promptVersion` populated
- feedback aggregate fields populated

### Step D2: prove retrieval-filter impact is reflected

Run the same question with a restrictive filter (for example JIRA-only in a local-file-only dataset):

```bash
curl -s --location 'http://localhost:8080/api/assistant/ask' \
  --header "Authorization: Bearer $USER_TOKEN" \
  --header 'Content-Type: application/json' \
  --data '{
    "question":"How does ingestion flow work?",
    "sourceTypes":["JIRA"]
  }' | jq '{citationsCount:(.citations|length), insufficientEvidence}'
```

Expected in most local-only setups:

- `citationsCount = 0`
- `insufficientEvidence = true`

This demonstrates the evaluation inputs are sensitive to retrieval constraints (not just model output).

### Step E: DB cross-check for cluster and aggregate correctness

```bash
psql "postgresql://postgres:postgres@localhost:5432/knowledge_copilot" -Atc "
SELECT arr.id, arr.question, arr.question_cluster_id, qc.normalized_question
FROM assistant_response_record arr
JOIN question_cluster qc ON qc.id = arr.question_cluster_id
WHERE arr.id IN ('$RID1','$RID2','$RID3')
ORDER BY arr.created_at;"
```

Interpretation for current threshold (`0.82`):

- `RID1`, `RID2`, and `RID3` are expected to share the same `question_cluster_id`.

Hard assertion query:

```bash
psql "postgresql://postgres:postgres@localhost:5432/knowledge_copilot" -Atc "
SELECT COUNT(DISTINCT arr.question_cluster_id)
FROM assistant_response_record arr
WHERE arr.id IN ('$RID1','$RID2','$RID3');"
```

Expected value: `1`

Now verify evaluation aggregate math for target cluster:

```bash
CLUSTER_ID=$(psql "postgresql://postgres:postgres@localhost:5432/knowledge_copilot" -Atc "
SELECT question_cluster_id FROM assistant_response_record WHERE id = '$RID1' LIMIT 1;")

psql "postgresql://postgres:postgres@localhost:5432/knowledge_copilot" -Atc "
SELECT
  SUM(CASE WHEN arf.verdict='GOOD' THEN 1 ELSE 0 END) AS good,
  SUM(CASE WHEN arf.verdict='BAD' THEN 1 ELSE 0 END) AS bad
FROM assistant_response_feedback arf
JOIN assistant_response_record arr ON arr.id = arf.response_id
WHERE arr.question_cluster_id = '$CLUSTER_ID';"
```

Compare this SQL result with the latest evaluation run’s:

- `questionFeedbackGoodCount`
- `questionFeedbackBadCount`
- `questionFeedbackGoodRate = good/(good+bad)`

### Step F: vote update behavior (no duplicate inflation)

Flip same user vote on `RID1`:

```bash
curl -s --location --request POST 'http://localhost:8080/api/assistant/feedback' \
  --header "Authorization: Bearer $USER_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "{\"responseId\":\"$RID1\",\"verdict\":\"BAD\",\"comment\":\"changed my mind\"}" | jq
```

Run evaluation again:

```bash
RUN2=$(curl -s --location --request POST "http://localhost:8080/api/admin/evaluations/runs/$GID" \
  --header "Authorization: Bearer $ADMIN_TOKEN")
echo "$RUN2" | jq '{questionFeedbackGoodCount,questionFeedbackBadCount,questionFeedbackGoodRate}'
```

Proof checkpoint: totals should reflect replacement, not additional duplicate count.

## 4) Why this has product value

This layer gives actionable diagnostics, not just pass/fail.

1. If `citationCorrectness` is low but `goodRate` is high: users still trust answers, likely expected-source list is too strict or incomplete.
2. If `citationCorrectness` is high but `goodRate` is low: retrieval is likely correct but answer quality/prompt/model is weak.
3. If both are low: retrieval or source quality issue.
4. Since runs store `modelUsed` and `promptVersion`, you can compare quality after prompt/model changes and avoid regressions.

## 5) Tuning semantic grouping

Two env vars control paraphrase merge strictness:

- `EVAL_QUESTION_CLUSTER_SIMILARITY_THRESHOLD` (default `0.86`)
- `EVAL_QUESTION_CLUSTER_CANDIDATE_POOL_SIZE` (default `20`)

Guidance:

1. If semantically same questions are not merging enough, reduce threshold (`0.86 -> 0.82`).
2. If unrelated questions merge incorrectly, increase threshold (`0.86 -> 0.90`).

## 6) Pass/fail rubric for stakeholder demo

Pass criteria:

1. `/ask` always returns `responseId`.
2. `/feedback` persists and returns stable aggregates.
3. Evaluation run includes all expected fields and non-empty retrieval/citation traces.
4. DB cross-check equals API aggregate counts.
5. Vote flip updates metrics deterministically.

If all five pass, the evaluation layer is functioning and decision-useful.

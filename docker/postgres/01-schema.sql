CREATE TABLE IF NOT EXISTS knowledge_document (
    id UUID PRIMARY KEY,
    source_type VARCHAR(50) NOT NULL,
    source_path TEXT NOT NULL UNIQUE,
    file_name TEXT NOT NULL,
    title TEXT,
    content_type VARCHAR(100),
    checksum VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ,
    indexed_at TIMESTAMPTZ NOT NULL,
    metadata_json TEXT
);

CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES knowledge_document(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    embedding_json TEXT NOT NULL,
    citation_label TEXT NOT NULL,
    metadata_json TEXT,
    UNIQUE(document_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS knowledge_chunk_document_id_idx
    ON knowledge_chunk (document_id);

CREATE TABLE IF NOT EXISTS golden_question (
    id UUID PRIMARY KEY,
    question TEXT NOT NULL,
    expected_sources_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS evaluation_run (
    id UUID PRIMARY KEY,
    golden_question_id UUID NOT NULL REFERENCES golden_question(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    expected_sources_json TEXT,
    retrieved_chunks_json TEXT,
    citations_json TEXT,
    generated_answer TEXT,
    citation_correctness DOUBLE PRECISION NOT NULL,
    expected_source_count INT NOT NULL,
    matched_expected_source_count INT NOT NULL,
    user_feedback_score INT,
    user_feedback_comment TEXT,
    question_feedback_good_count INT NOT NULL DEFAULT 0,
    question_feedback_bad_count INT NOT NULL DEFAULT 0,
    question_feedback_good_rate DOUBLE PRECISION,
    latency_ms BIGINT NOT NULL,
    model_used VARCHAR(120) NOT NULL,
    prompt_version VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS evaluation_run_golden_question_id_idx
    ON evaluation_run (golden_question_id);

CREATE INDEX IF NOT EXISTS evaluation_run_created_at_idx
    ON evaluation_run (created_at DESC);

CREATE TABLE IF NOT EXISTS question_cluster (
    id UUID PRIMARY KEY,
    normalized_question TEXT NOT NULL UNIQUE,
    representative_question TEXT NOT NULL,
    embedding_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS assistant_response_record (
    id UUID PRIMARY KEY,
    asked_by VARCHAR(120) NOT NULL,
    question_cluster_id UUID,
    question TEXT NOT NULL,
    generated_answer TEXT NOT NULL,
    citations_json TEXT,
    retrieved_chunks_json TEXT,
    latency_ms BIGINT NOT NULL,
    model_used VARCHAR(120) NOT NULL,
    prompt_version VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE assistant_response_record
    ADD COLUMN IF NOT EXISTS question_cluster_id UUID;

INSERT INTO question_cluster (id, normalized_question, representative_question, embedding_json, created_at, updated_at)
SELECT
    (
        substr(md5(btrim(regexp_replace(lower(regexp_replace(arr.question, '[^a-z0-9\\s]', ' ', 'g')), '\\s+', ' ', 'g'))), 1, 8) || '-' ||
        substr(md5(btrim(regexp_replace(lower(regexp_replace(arr.question, '[^a-z0-9\\s]', ' ', 'g')), '\\s+', ' ', 'g'))), 9, 4) || '-' ||
        substr(md5(btrim(regexp_replace(lower(regexp_replace(arr.question, '[^a-z0-9\\s]', ' ', 'g')), '\\s+', ' ', 'g'))), 13, 4) || '-' ||
        substr(md5(btrim(regexp_replace(lower(regexp_replace(arr.question, '[^a-z0-9\\s]', ' ', 'g')), '\\s+', ' ', 'g'))), 17, 4) || '-' ||
        substr(md5(btrim(regexp_replace(lower(regexp_replace(arr.question, '[^a-z0-9\\s]', ' ', 'g')), '\\s+', ' ', 'g'))), 21, 12)
    )::uuid,
    btrim(regexp_replace(lower(regexp_replace(arr.question, '[^a-z0-9\\s]', ' ', 'g')), '\\s+', ' ', 'g')),
    arr.question,
    '[]',
    now(),
    now()
FROM (
    SELECT DISTINCT question
    FROM assistant_response_record
    WHERE question IS NOT NULL AND btrim(question) <> ''
) arr
WHERE NOT EXISTS (
    SELECT 1
    FROM question_cluster qc
    WHERE qc.normalized_question = btrim(regexp_replace(lower(regexp_replace(arr.question, '[^a-z0-9\\s]', ' ', 'g')), '\\s+', ' ', 'g'))
);

INSERT INTO question_cluster (id, normalized_question, representative_question, embedding_json, created_at, updated_at)
SELECT
    '00000000-0000-0000-0000-000000000001'::uuid,
    '__empty_question__',
    '__empty_question__',
    '[]',
    now(),
    now()
WHERE NOT EXISTS (
    SELECT 1 FROM question_cluster WHERE normalized_question = '__empty_question__'
);

UPDATE assistant_response_record
SET question_cluster_id = (
    SELECT qc.id
    FROM question_cluster qc
    WHERE qc.normalized_question = btrim(regexp_replace(lower(regexp_replace(assistant_response_record.question, '[^a-z0-9\\s]', ' ', 'g')), '\\s+', ' ', 'g'))
    LIMIT 1
)
WHERE question_cluster_id IS NULL;

UPDATE assistant_response_record
SET question_cluster_id = (SELECT id FROM question_cluster WHERE normalized_question = '__empty_question__' LIMIT 1)
WHERE question_cluster_id IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'assistant_response_record_question_cluster_id_fkey'
    ) THEN
        ALTER TABLE assistant_response_record
            ADD CONSTRAINT assistant_response_record_question_cluster_id_fkey
            FOREIGN KEY (question_cluster_id) REFERENCES question_cluster(id) ON DELETE RESTRICT;
    END IF;
END $$;

ALTER TABLE assistant_response_record
    ALTER COLUMN question_cluster_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS assistant_response_record_question_cluster_id_idx
    ON assistant_response_record (question_cluster_id);

CREATE TABLE IF NOT EXISTS assistant_response_feedback (
    id UUID PRIMARY KEY,
    response_id UUID NOT NULL REFERENCES assistant_response_record(id) ON DELETE CASCADE,
    submitted_by VARCHAR(120) NOT NULL,
    verdict VARCHAR(20) NOT NULL,
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT assistant_response_feedback_response_id_user_key UNIQUE (response_id, submitted_by)
);

CREATE INDEX IF NOT EXISTS assistant_response_feedback_response_id_idx
    ON assistant_response_feedback (response_id);

CREATE INDEX IF NOT EXISTS assistant_response_record_question_idx
    ON assistant_response_record (question);

CREATE TABLE IF NOT EXISTS audit_event (
    id UUID PRIMARY KEY,
    actor VARCHAR(120) NOT NULL,
    action VARCHAR(120) NOT NULL,
    resource_type VARCHAR(120),
    resource_id VARCHAR(200),
    method VARCHAR(12) NOT NULL,
    path TEXT NOT NULL,
    status_code INT NOT NULL,
    request_id VARCHAR(120),
    metadata_json TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS audit_event_created_at_idx
    ON audit_event (created_at DESC);

CREATE INDEX IF NOT EXISTS audit_event_action_created_at_idx
    ON audit_event (action, created_at DESC);

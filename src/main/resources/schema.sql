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
    question_cluster_id UUID NOT NULL REFERENCES question_cluster(id) ON DELETE RESTRICT,
    question TEXT NOT NULL,
    generated_answer TEXT NOT NULL,
    citations_json TEXT,
    retrieved_chunks_json TEXT,
    latency_ms BIGINT NOT NULL,
    model_used VARCHAR(120) NOT NULL,
    prompt_version VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

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

CREATE INDEX IF NOT EXISTS assistant_response_record_question_cluster_id_idx
    ON assistant_response_record (question_cluster_id);

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

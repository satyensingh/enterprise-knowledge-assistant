package com.example.knowledgecopilot.observability;

public final class MetricNames {
    private MetricNames() {}

    public static final String ASK_REQUESTS_TOTAL = "kc.ask.requests.total";
    public static final String ASK_LATENCY = "kc.ask.latency";
    public static final String ASK_RESPONSE_CHARS = "kc.ask.response.chars";
    public static final String ASK_INSUFFICIENT_ANSWERS_TOTAL = "kc.ask.insufficient.answers.total";

    public static final String RETRIEVAL_LATENCY = "kc.retrieval.latency";
    public static final String RETRIEVAL_CHUNKS = "kc.retrieval.chunks";
    public static final String RETRIEVAL_AUTHORIZED_CHUNKS = "kc.retrieval.authorized.chunks";
    public static final String RETRIEVAL_CITATIONS = "kc.retrieval.citations";

    public static final String EVALUATION_RUNS_TOTAL = "kc.evaluation.runs.total";
    public static final String EVALUATION_LATENCY = "kc.evaluation.latency";
    public static final String EVALUATION_CITATION_CORRECTNESS = "kc.evaluation.citation.correctness";
    public static final String EVALUATION_EXPECTED_SOURCES = "kc.evaluation.expected.sources";
    public static final String EVALUATION_MATCHED_SOURCES = "kc.evaluation.matched.sources";

    public static final String FEEDBACK_SUBMISSIONS_TOTAL = "kc.feedback.submissions.total";
    public static final String FEEDBACK_GOOD_RATE = "kc.feedback.good.rate";
    public static final String FEEDBACK_SCORE = "kc.feedback.score";

    public static final String QUESTION_CLUSTER_RESOLUTION_TOTAL = "kc.question.cluster.resolution.total";
    public static final String QUESTION_CLUSTER_SIMILARITY = "kc.question.cluster.similarity";

    public static final String SECURITY_AUTH_FAILURES_TOTAL = "kc.security.auth.failures.total";
    public static final String SECURITY_RATE_LIMIT_REJECTIONS_TOTAL = "kc.security.rate.limit.rejections.total";
    public static final String AUDIT_EVENTS_TOTAL = "kc.audit.events.total";

    public static final String OPENAI_LLM_REQUESTS_TOTAL = "kc.openai.llm.requests.total";
    public static final String OPENAI_LLM_LATENCY = "kc.openai.llm.latency";
    public static final String OPENAI_LLM_USAGE_TOKENS = "kc.openai.llm.usage.tokens";

    public static final String OPENAI_EMBEDDING_REQUESTS_TOTAL = "kc.openai.embedding.requests.total";
    public static final String OPENAI_EMBEDDING_LATENCY = "kc.openai.embedding.latency";
    public static final String OPENAI_EMBEDDING_USAGE_TOKENS = "kc.openai.embedding.usage.tokens";

    public static final String INGESTION_REINDEX_TOTAL = "kc.ingestion.reindex.total";
    public static final String INGESTION_REINDEX_LATENCY = "kc.ingestion.reindex.latency";
    public static final String INGESTION_CONNECTOR_SYNC_LATENCY = "kc.ingestion.connector.sync.latency";
    public static final String INGESTION_CONNECTOR_ITEMS = "kc.ingestion.connector.items";
    public static final String INGESTION_CONNECTOR_DOCUMENTS_TOTAL = "kc.ingestion.connector.documents.total";
    public static final String INGESTION_DOCUMENTS_TOTAL = "kc.ingestion.documents.total";
    public static final String INGESTION_DOCUMENT_LATENCY = "kc.ingestion.document.latency";
    public static final String INGESTION_CHUNKS_TOTAL = "kc.ingestion.chunks.total";
    public static final String INGESTION_PRUNED_DOCUMENTS_TOTAL = "kc.ingestion.pruned.documents.total";
}

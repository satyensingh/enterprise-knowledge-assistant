package com.example.knowledgecopilot.retrieval.api;

import java.util.Map;

public record VectorRecord(
    String id,
    float[] embedding,
    String document,
    Map<String, Object> metadata
) {
}

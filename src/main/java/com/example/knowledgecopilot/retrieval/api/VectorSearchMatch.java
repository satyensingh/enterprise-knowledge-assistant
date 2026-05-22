package com.example.knowledgecopilot.retrieval.api;

public record VectorSearchMatch(
    String id,
    double distance
) {
}

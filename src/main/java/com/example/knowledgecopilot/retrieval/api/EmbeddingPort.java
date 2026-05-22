package com.example.knowledgecopilot.retrieval.api;

public interface EmbeddingPort {
    float[] embed(String text);
}

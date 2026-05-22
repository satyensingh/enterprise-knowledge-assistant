package com.example.knowledgecopilot.retrieval.api;

import java.util.List;

public interface VectorStorePort {
    void upsertBatch(List<VectorRecord> records);
    void deleteByIds(List<String> ids);
    List<VectorSearchMatch> query(float[] embedding, int limit);
}

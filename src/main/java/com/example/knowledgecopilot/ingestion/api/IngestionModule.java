package com.example.knowledgecopilot.ingestion.api;

public interface IngestionModule {
    void reindexAllFiles(String trigger);
}

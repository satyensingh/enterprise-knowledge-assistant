package com.example.knowledgecopilot.ingestion.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SourceConnector {
    String connectorType();
    boolean testConnection();
    List<NormalizedSourceItem> fullSync();
    List<NormalizedSourceItem> incrementalSync(Instant since);
    Optional<NormalizedSourceItem> fetchItem(String externalId);
    Set<String> fetchPermissions(String externalId);
    void markDeleted(String externalId);
}

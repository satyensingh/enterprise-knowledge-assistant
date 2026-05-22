package com.example.knowledgecopilot.ingestion.api;

import java.nio.file.Path;
import java.time.Instant;

public record NormalizedSourceItem(
    String externalId,
    Path path,
    String title,
    String sourceType,
    Instant updatedAt,
    boolean deleted,
    String content,
    String contentType
) {
}

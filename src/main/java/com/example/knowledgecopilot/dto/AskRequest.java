package com.example.knowledgecopilot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "Ask request payload with optional retrieval filters.")
public class AskRequest {
    @NotBlank
    @Schema(example = "How does ingestion flow work?")
    private String question;

    @Schema(description = "Filter by source types such as LOCAL_FILE_SYSTEM, JIRA, CONFLUENCE.")
    private List<String> sourceTypes;
    @Schema(description = "Filter by source paths.")
    private List<String> sourcePaths;
    @Schema(description = "Filter by workspace IDs.")
    private List<String> workspaceIds;
    @Schema(description = "Only include documents updated after this timestamp (inclusive).")
    private Instant updatedAfter;
    @Schema(description = "Only include documents updated before this timestamp (inclusive).")
    private Instant updatedBefore;
    @Schema(description = "Metadata key/value equals filters.")
    private Map<String, String> metadataFilters;
    @Schema(description = "Connector-specific key/value filters.")
    private Map<String, String> connectorFilters;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<String> getSourceTypes() { return sourceTypes; }
    public void setSourceTypes(List<String> sourceTypes) { this.sourceTypes = sourceTypes; }
    public List<String> getSourcePaths() { return sourcePaths; }
    public void setSourcePaths(List<String> sourcePaths) { this.sourcePaths = sourcePaths; }
    public List<String> getWorkspaceIds() { return workspaceIds; }
    public void setWorkspaceIds(List<String> workspaceIds) { this.workspaceIds = workspaceIds; }
    public Instant getUpdatedAfter() { return updatedAfter; }
    public void setUpdatedAfter(Instant updatedAfter) { this.updatedAfter = updatedAfter; }
    public Instant getUpdatedBefore() { return updatedBefore; }
    public void setUpdatedBefore(Instant updatedBefore) { this.updatedBefore = updatedBefore; }
    public Map<String, String> getMetadataFilters() { return metadataFilters; }
    public void setMetadataFilters(Map<String, String> metadataFilters) { this.metadataFilters = metadataFilters; }
    public Map<String, String> getConnectorFilters() { return connectorFilters; }
    public void setConnectorFilters(Map<String, String> connectorFilters) { this.connectorFilters = connectorFilters; }
}

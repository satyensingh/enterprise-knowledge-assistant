package com.example.knowledgecopilot.retrieval.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class RetrievalQuery {
    private String question;
    private List<String> sourceTypes;
    private List<String> sourcePaths;
    private List<String> workspaceIds;
    private Instant updatedAfter;
    private Instant updatedBefore;
    private Map<String, String> metadataFilters;
    private Map<String, String> connectorFilters;

    public RetrievalQuery() {}

    public RetrievalQuery(
        String question,
        List<String> sourceTypes,
        List<String> sourcePaths,
        List<String> workspaceIds,
        Instant updatedAfter,
        Instant updatedBefore,
        Map<String, String> metadataFilters,
        Map<String, String> connectorFilters
    ) {
        this.question = question;
        this.sourceTypes = sourceTypes;
        this.sourcePaths = sourcePaths;
        this.workspaceIds = workspaceIds;
        this.updatedAfter = updatedAfter;
        this.updatedBefore = updatedBefore;
        this.metadataFilters = metadataFilters;
        this.connectorFilters = connectorFilters;
    }

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

package com.example.knowledgecopilot.retrieval.internal;

import com.example.knowledgecopilot.entity.KnowledgeChunk;
import com.example.knowledgecopilot.retrieval.api.RetrievalQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;

@Service
public class RetrievalFilterService {
    private final ObjectMapper objectMapper;

    public RetrievalFilterService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<KnowledgeChunk> applyFilters(List<KnowledgeChunk> chunks, RetrievalQuery query) {
        if (chunks == null || chunks.isEmpty() || query == null) {
            return chunks == null ? List.of() : chunks;
        }

        Set<String> sourceTypes = normalizedSet(query.getSourceTypes());
        Set<String> sourcePaths = normalizedSet(query.getSourcePaths());
        Set<String> workspaceIds = normalizedSet(query.getWorkspaceIds());
        Map<String, String> metadataFilters = normalizedMap(query.getMetadataFilters());
        Map<String, String> connectorFilters = normalizedMap(query.getConnectorFilters());
        Instant updatedAfter = query.getUpdatedAfter();
        Instant updatedBefore = query.getUpdatedBefore();

        return chunks.stream()
            .filter(chunk -> matchesSourceType(chunk, sourceTypes))
            .filter(chunk -> matchesSourcePath(chunk, sourcePaths))
            .filter(chunk -> matchesUpdatedAt(chunk, updatedAfter, updatedBefore))
            .filter(chunk -> matchesWorkspace(chunk, workspaceIds))
            .filter(chunk -> matchesMetadata(chunk, metadataFilters))
            .filter(chunk -> matchesConnectorFilters(chunk, connectorFilters))
            .toList();
    }

    private boolean matchesSourceType(KnowledgeChunk chunk, Set<String> sourceTypes) {
        if (sourceTypes.isEmpty()) {
            return true;
        }
        String value = safeLower(chunk.getDocument().getSourceType());
        return sourceTypes.contains(value);
    }

    private boolean matchesSourcePath(KnowledgeChunk chunk, Set<String> sourcePaths) {
        if (sourcePaths.isEmpty()) {
            return true;
        }
        String sourcePath = safeLower(chunk.getDocument().getSourcePath());
        return sourcePaths.stream()
            .anyMatch(filter -> sourcePath.startsWith(filter) || sourcePath.equals(filter));
    }

    private boolean matchesUpdatedAt(KnowledgeChunk chunk, Instant updatedAfter, Instant updatedBefore) {
        if (updatedAfter == null && updatedBefore == null) {
            return true;
        }
        Instant updatedAt = chunk.getDocument().getUpdatedAt();
        if (updatedAt == null) {
            updatedAt = chunk.getDocument().getIndexedAt();
        }
        if (updatedAt == null) {
            return false;
        }
        if (updatedAfter != null && updatedAt.isBefore(updatedAfter)) {
            return false;
        }
        if (updatedBefore != null && updatedAt.isAfter(updatedBefore)) {
            return false;
        }
        return true;
    }

    private boolean matchesWorkspace(KnowledgeChunk chunk, Set<String> workspaceIds) {
        if (workspaceIds.isEmpty()) {
            return true;
        }
        JsonNode metadata = readMetadata(chunk.getDocument().getMetadataJson());
        List<String> candidates = List.of(
            safeLower(textValue(metadata.path("workspaceId"))),
            safeLower(textValue(metadata.path("workspace"))),
            safeLower(textValue(metadata.path("spaceId"))),
            safeLower(textValue(metadata.path("space"))),
            extractWorkspaceFromSourcePath(chunk.getDocument().getSourcePath())
        );
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && workspaceIds.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesMetadata(KnowledgeChunk chunk, Map<String, String> metadataFilters) {
        if (metadataFilters.isEmpty()) {
            return true;
        }
        JsonNode documentMetadata = readMetadata(chunk.getDocument().getMetadataJson());
        JsonNode chunkMetadata = readMetadata(chunk.getMetadataJson());
        for (Map.Entry<String, String> filter : metadataFilters.entrySet()) {
            String expected = filter.getValue();
            if (expected == null || expected.isBlank()) {
                continue;
            }
            String key = filter.getKey();
            String documentValue = valueByPath(documentMetadata, key);
            String chunkValue = valueByPath(chunkMetadata, key);
            if (!expected.equalsIgnoreCase(documentValue) && !expected.equalsIgnoreCase(chunkValue)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesConnectorFilters(KnowledgeChunk chunk, Map<String, String> connectorFilters) {
        if (connectorFilters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, String> filter : connectorFilters.entrySet()) {
            String key = safeLower(filter.getKey());
            String expected = filter.getValue();
            if (expected == null || expected.isBlank()) {
                continue;
            }
            if ("connector".equals(key) || "sourceType".equalsIgnoreCase(key)) {
                if (!expected.equalsIgnoreCase(chunk.getDocument().getSourceType())) {
                    return false;
                }
                continue;
            }
            if ("sourcePathContains".equalsIgnoreCase(key)) {
                String sourcePath = chunk.getDocument().getSourcePath();
                if (sourcePath == null || !sourcePath.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT))) {
                    return false;
                }
                continue;
            }
            if ("sourcePathPrefix".equalsIgnoreCase(key)) {
                String sourcePath = chunk.getDocument().getSourcePath();
                if (sourcePath == null || !sourcePath.toLowerCase(Locale.ROOT).startsWith(expected.toLowerCase(Locale.ROOT))) {
                    return false;
                }
                continue;
            }

            Map<String, String> passthrough = Map.of(filter.getKey(), filter.getValue());
            if (!matchesMetadata(chunk, passthrough)) {
                return false;
            }
        }
        return true;
    }

    private JsonNode readMetadata(String json) {
        if (!StringUtils.hasText(json)) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return objectMapper.nullNode();
        }
    }

    private String valueByPath(JsonNode root, String path) {
        if (root == null || root.isNull() || path == null || path.isBlank()) {
            return "";
        }
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            current = current.path(segment.trim());
            if (current.isMissingNode() || current.isNull()) {
                return "";
            }
        }
        return textValue(current);
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return "";
    }

    private Set<String> normalizedSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    private Map<String, String> normalizedMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank() || value == null || value.isBlank()) {
                continue;
            }
            normalized.put(key.trim(), value.trim());
        }
        return normalized;
    }

    private String extractWorkspaceFromSourcePath(String sourcePath) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return "";
        }
        String normalized = sourcePath.replace("\\", "/");
        String[] segments = normalized.split("/");
        if (segments.length >= 2) {
            return safeLower(segments[segments.length - 2]);
        }
        return safeLower(normalized);
    }

    private String safeLower(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}

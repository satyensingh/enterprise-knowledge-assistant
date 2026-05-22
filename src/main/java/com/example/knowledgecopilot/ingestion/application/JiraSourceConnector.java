package com.example.knowledgecopilot.ingestion.application;

import com.example.knowledgecopilot.config.AppProperties;
import com.example.knowledgecopilot.ingestion.api.NormalizedSourceItem;
import com.example.knowledgecopilot.ingestion.api.SourceConnector;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "app.ingestion.jira", name = "enabled", havingValue = "true")
public class JiraSourceConnector implements SourceConnector {
    private static final DateTimeFormatter JIRA_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
    private static final int PAGE_SIZE = 50;

    private final AppProperties properties;

    public JiraSourceConnector(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public String connectorType() {
        return "JIRA";
    }

    @Override
    public boolean testConnection() {
        AppProperties.Ingestion.Jira jira = properties.getIngestion().getJira();
        if (!isConfigured(jira)) {
            return false;
        }

        try {
            JsonNode response = client(jira)
                .get()
                .uri("/rest/api/3/myself")
                .retrieve()
                .body(JsonNode.class);
            return response != null && !response.path("accountId").asText("").isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<NormalizedSourceItem> fullSync() {
        AppProperties.Ingestion.Jira jira = properties.getIngestion().getJira();
        if (!isConfigured(jira)) {
            return List.of();
        }

        List<NormalizedSourceItem> items = new ArrayList<>();
        int startAt = 0;
        int total = Integer.MAX_VALUE;

        while (startAt < total) {
            int currentStartAt = startAt;
            JsonNode response = client(jira)
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/rest/api/3/search/jql")
                    .queryParam("jql", jira.getJql())
                    .queryParam("startAt", currentStartAt)
                    .queryParam("maxResults", PAGE_SIZE)
                    .queryParam("fields", "summary,description,updated")
                    .build())
                .retrieve()
                .body(JsonNode.class);

            if (response == null) {
                break;
            }

            JsonNode issues = response.path("issues");
            if (!issues.isArray() || issues.isEmpty()) {
                break;
            }

            for (JsonNode issue : issues) {
                items.add(toNormalizedItem(issue));
            }

            total = response.path("total").asInt(issues.size());
            startAt += issues.size();
        }

        return items;
    }

    @Override
    public List<NormalizedSourceItem> incrementalSync(Instant since) {
        return fullSync().stream()
            .filter(item -> !item.updatedAt().isBefore(since))
            .toList();
    }

    @Override
    public Optional<NormalizedSourceItem> fetchItem(String externalId) {
        AppProperties.Ingestion.Jira jira = properties.getIngestion().getJira();
        if (!isConfigured(jira) || externalId == null || !externalId.startsWith("JIRA:")) {
            return Optional.empty();
        }

        String issueKey = externalId.substring("JIRA:".length());
        JsonNode issue = client(jira)
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/rest/api/3/issue/{issueKey}")
                .queryParam("fields", "summary,description,updated")
                .build(issueKey))
            .retrieve()
            .body(JsonNode.class);

        if (issue == null || issue.isMissingNode()) {
            return Optional.empty();
        }

        return Optional.of(toNormalizedItem(issue));
    }

    @Override
    public Set<String> fetchPermissions(String externalId) {
        return Set.of("jira:default");
    }

    @Override
    public void markDeleted(String externalId) {
    }

    private RestClient client(AppProperties.Ingestion.Jira jira) {
        String authToken = Base64.getEncoder().encodeToString(
            (jira.getEmail() + ":" + jira.getApiToken()).getBytes(StandardCharsets.UTF_8)
        );

        return RestClient.builder()
            .baseUrl(jira.getBaseUrl())
            .defaultHeader("Authorization", "Basic " + authToken)
            .defaultHeader("Accept", "application/json")
            .build();
    }

    private boolean isConfigured(AppProperties.Ingestion.Jira jira) {
        return jira != null
            && jira.isEnabled()
            && jira.getBaseUrl() != null && !jira.getBaseUrl().isBlank()
            && jira.getEmail() != null && !jira.getEmail().isBlank()
            && jira.getApiToken() != null && !jira.getApiToken().isBlank();
    }

    private NormalizedSourceItem toNormalizedItem(JsonNode issue) {
        String issueKey = issue.path("key").asText();
        JsonNode fields = issue.path("fields");
        String summary = fields.path("summary").asText(issueKey);
        String updated = fields.path("updated").asText();
        Instant updatedAt = parseUpdatedAt(updated);
        String description = extractText(fields.path("description")).trim();
        String content = "Issue: " + issueKey + "\nSummary: " + summary + "\n\nDescription:\n" + description;

        return new NormalizedSourceItem(
            "JIRA:" + issueKey,
            null,
            issueKey + " - " + summary,
            connectorType(),
            updatedAt,
            false,
            content,
            "text/plain"
        );
    }

    private Instant parseUpdatedAt(String updated) {
        if (updated == null || updated.isBlank()) {
            return Instant.now();
        }
        try {
            return OffsetDateTime.parse(updated, JIRA_TIMESTAMP).toInstant();
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private String extractText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }

        StringBuilder builder = new StringBuilder();
        JsonNode textNode = node.get("text");
        if (textNode != null && textNode.isTextual()) {
            builder.append(textNode.asText()).append('\n');
        }

        JsonNode content = node.get("content");
        if (content != null && content.isArray()) {
            for (JsonNode child : content) {
                builder.append(extractText(child));
            }
        }

        return builder.toString();
    }
}

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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "app.ingestion.confluence", name = "enabled", havingValue = "true")
public class ConfluenceSourceConnector implements SourceConnector {
    private static final int PAGE_SIZE = 50;

    private final AppProperties properties;

    public ConfluenceSourceConnector(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public String connectorType() {
        return "CONFLUENCE";
    }

    @Override
    public boolean testConnection() {
        AppProperties.Ingestion.Confluence confluence = properties.getIngestion().getConfluence();
        if (!isConfigured(confluence)) {
            return false;
        }

        try {
            JsonNode response = client(confluence)
                .get()
                .uri("/wiki/rest/api/user/current")
                .retrieve()
                .body(JsonNode.class);
            return response != null && !response.path("accountId").asText("").isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<NormalizedSourceItem> fullSync() {
        AppProperties.Ingestion.Confluence confluence = properties.getIngestion().getConfluence();
        if (!isConfigured(confluence)) {
            return List.of();
        }

        List<NormalizedSourceItem> items = new ArrayList<>();
        int start = 0;

        while (true) {
            int currentStart = start;
            JsonNode response = client(confluence)
                .get()
                .uri(uriBuilder -> uriBuilder
                    .path("/wiki/rest/api/content/search")
                    .queryParam("cql", confluence.getCql())
                    .queryParam("start", currentStart)
                    .queryParam("limit", PAGE_SIZE)
                    .queryParam("expand", "body.storage,version,space")
                    .build())
                .retrieve()
                .body(JsonNode.class);

            if (response == null) {
                break;
            }

            JsonNode results = response.path("results");
            if (!results.isArray() || results.isEmpty()) {
                break;
            }

            for (JsonNode page : results) {
                items.add(toNormalizedItem(page));
            }

            int pageSize = results.size();
            if (pageSize < PAGE_SIZE) {
                break;
            }
            start += pageSize;
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
        AppProperties.Ingestion.Confluence confluence = properties.getIngestion().getConfluence();
        if (!isConfigured(confluence) || externalId == null || !externalId.startsWith("CONFLUENCE:")) {
            return Optional.empty();
        }

        String contentId = externalId.substring("CONFLUENCE:".length());
        JsonNode page = client(confluence)
            .get()
            .uri(uriBuilder -> uriBuilder
                .path("/wiki/rest/api/content/{id}")
                .queryParam("expand", "body.storage,version,space")
                .build(contentId))
            .retrieve()
            .body(JsonNode.class);

        if (page == null || page.isMissingNode()) {
            return Optional.empty();
        }

        return Optional.of(toNormalizedItem(page));
    }

    @Override
    public Set<String> fetchPermissions(String externalId) {
        return Set.of("confluence:default");
    }

    @Override
    public void markDeleted(String externalId) {
    }

    private RestClient client(AppProperties.Ingestion.Confluence confluence) {
        String authToken = Base64.getEncoder().encodeToString(
            (confluence.getEmail() + ":" + confluence.getApiToken()).getBytes(StandardCharsets.UTF_8)
        );

        return RestClient.builder()
            .baseUrl(confluence.getBaseUrl())
            .defaultHeader("Authorization", "Basic " + authToken)
            .defaultHeader("Accept", "application/json")
            .build();
    }

    private boolean isConfigured(AppProperties.Ingestion.Confluence confluence) {
        return confluence != null
            && confluence.isEnabled()
            && confluence.getBaseUrl() != null && !confluence.getBaseUrl().isBlank()
            && confluence.getEmail() != null && !confluence.getEmail().isBlank()
            && confluence.getApiToken() != null && !confluence.getApiToken().isBlank();
    }

    private NormalizedSourceItem toNormalizedItem(JsonNode page) {
        String id = page.path("id").asText();
        String title = page.path("title").asText("Untitled");
        String pageType = page.path("type").asText("page");
        String spaceName = page.path("space").path("name").asText("");
        String html = page.path("body").path("storage").path("value").asText("");
        String text = htmlToText(html);
        Instant updatedAt = parseUpdatedAt(page.path("version").path("when").asText(""));

        StringBuilder content = new StringBuilder();
        content.append("Title: ").append(title).append("\n");
        content.append("Type: ").append(pageType).append("\n");
        if (!spaceName.isBlank()) {
            content.append("Space: ").append(spaceName).append("\n");
        }
        content.append("\n").append(text);

        return new NormalizedSourceItem(
            "CONFLUENCE:" + id,
            null,
            title,
            connectorType(),
            updatedAt,
            false,
            content.toString(),
            "text/plain"
        );
    }

    private Instant parseUpdatedAt(String updated) {
        if (updated == null || updated.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(updated);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return html
            .replaceAll("(?is)<script.*?>.*?</script>", " ")
            .replaceAll("(?is)<style.*?>.*?</style>", " ")
            .replaceAll("(?is)<br\\s*/?>", "\n")
            .replaceAll("(?is)</p>", "\n")
            .replaceAll("(?is)<[^>]+>", " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
            .replaceAll("\\n\\s*\\n+", "\n\n")
            .trim();
    }
}

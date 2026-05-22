package com.example.knowledgecopilot.retrieval.internal;

import com.example.knowledgecopilot.config.AppProperties;
import com.example.knowledgecopilot.retrieval.api.VectorRecord;
import com.example.knowledgecopilot.retrieval.api.VectorSearchMatch;
import com.example.knowledgecopilot.retrieval.api.VectorStorePort;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChromaVectorStoreService implements VectorStorePort {
    private static final Logger log = LoggerFactory.getLogger(ChromaVectorStoreService.class);

    private final AppProperties properties;
    private volatile String collectionId;

    public ChromaVectorStoreService(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void upsertBatch(List<VectorRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        if (!properties.getVectorStore().isEnabled()) {
            return;
        }

        String id = ensureCollectionId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ids", records.stream().map(VectorRecord::id).toList());
        payload.put(
            "embeddings",
            records.stream().map(record -> toFloatList(record.embedding())).toList()
        );
        payload.put("documents", records.stream().map(VectorRecord::document).toList());
        payload.put("metadatas", records.stream().map(record -> safeMetadata(record.metadata())).toList());

        client().post()
            .uri(collectionActionPath(id, "upsert"))
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .toBodilessEntity();
    }

    @Override
    public void deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        if (!properties.getVectorStore().isEnabled()) {
            return;
        }

        String id = ensureCollectionId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ids", ids);

        client().post()
            .uri(collectionActionPath(id, "delete"))
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .toBodilessEntity();
    }

    @Override
    public List<VectorSearchMatch> query(float[] embedding, int limit) {
        if (embedding == null || embedding.length == 0 || limit <= 0) {
            return List.of();
        }
        if (!properties.getVectorStore().isEnabled()) {
            return List.of();
        }

        String id = ensureCollectionId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query_embeddings", List.of(toFloatList(embedding)));
        payload.put("n_results", limit);
        payload.put("include", List.of("distances"));

        JsonNode response = client().post()
            .uri(collectionActionPath(id, "query"))
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .body(JsonNode.class);

        return parseMatches(response);
    }

    private synchronized String ensureCollectionId() {
        if (collectionId != null && !collectionId.isBlank()) {
            return collectionId;
        }

        String collectionName = properties.getVectorStore().getChroma().getCollectionName();
        String existingId = findCollectionIdByName(collectionName);
        if (existingId != null && !existingId.isBlank()) {
            collectionId = existingId;
            return collectionId;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", collectionName);
        payload.put("metadata", Map.of("hnsw:space", "cosine"));

        JsonNode created = client().post()
            .uri(collectionRootPath())
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .body(JsonNode.class);

        String createdId = readCollectionId(created);
        collectionId = createdId == null || createdId.isBlank() ? collectionName : createdId;
        log.info("Initialized Chroma collection id={} name={}", collectionId, collectionName);
        return collectionId;
    }

    private String findCollectionIdByName(String name) {
        JsonNode response = client().get()
            .uri(collectionRootPath())
            .retrieve()
            .body(JsonNode.class);

        JsonNode collectionArray = response;
        if (response != null && response.isObject()) {
            collectionArray = response.path("collections");
            if (!collectionArray.isArray()) {
                collectionArray = response.path("data");
            }
        }

        if (collectionArray == null || !collectionArray.isArray()) {
            return null;
        }

        for (JsonNode collection : collectionArray) {
            if (name.equals(collection.path("name").asText())) {
                String id = readCollectionId(collection);
                return id == null || id.isBlank() ? name : id;
            }
        }
        return null;
    }

    private String readCollectionId(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String id = node.path("id").asText(null);
        if (id != null && !id.isBlank()) {
            return id;
        }
        return node.path("collection_id").asText(null);
    }

    private List<VectorSearchMatch> parseMatches(JsonNode response) {
        if (response == null || response.isNull()) {
            return List.of();
        }

        JsonNode idsNode = response.path("ids");
        JsonNode distancesNode = response.path("distances");
        List<String> ids = flattenStringArray(idsNode);
        List<Double> distances = flattenDoubleArray(distancesNode);

        List<VectorSearchMatch> matches = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (id == null || id.isBlank()) {
                continue;
            }
            double distance = i < distances.size() ? distances.get(i) : 0.0d;
            matches.add(new VectorSearchMatch(id, distance));
        }
        return matches;
    }

    private List<String> flattenStringArray(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        if (node.isArray() && node.size() > 0 && node.get(0).isArray()) {
            for (JsonNode child : node.get(0)) {
                values.add(child.asText());
            }
            return values;
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                values.add(child.asText());
            }
        }
        return values;
    }

    private List<Double> flattenDoubleArray(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }

        List<Double> values = new ArrayList<>();
        if (node.isArray() && node.size() > 0 && node.get(0).isArray()) {
            for (JsonNode child : node.get(0)) {
                values.add(child.asDouble());
            }
            return values;
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                values.add(child.asDouble());
            }
        }
        return values;
    }

    private List<Float> toFloatList(float[] values) {
        List<Float> result = new ArrayList<>(values.length);
        for (float value : values) {
            result.add(value);
        }
        return result;
    }

    private Map<String, Object> safeMetadata(Map<String, Object> metadata) {
        if (metadata == null) {
            return Map.of();
        }
        return metadata;
    }

    private String collectionRootPath() {
        String version = normalizeVersion(properties.getVectorStore().getChroma().getApiVersion());
        if ("v2".equals(version)) {
            var chroma = properties.getVectorStore().getChroma();
            return "/api/v2/tenants/" + chroma.getTenant() + "/databases/" + chroma.getDatabase() + "/collections";
        }
        return "/api/v1/collections";
    }

    private String collectionActionPath(String collectionId, String action) {
        return collectionRootPath() + "/" + collectionId + "/" + action;
    }

    private String normalizeVersion(String version) {
        if (version == null || version.isBlank()) {
            return "v1";
        }
        return version.trim().toLowerCase();
    }

    private RestClient client() {
        AppProperties.VectorStore.Chroma chroma = properties.getVectorStore().getChroma();
        RestClient.Builder builder = RestClient.builder().baseUrl(chroma.getBaseUrl());
        if (chroma.getApiToken() != null && !chroma.getApiToken().isBlank()) {
            builder.defaultHeader("x-chroma-token", chroma.getApiToken());
        }
        return builder.build();
    }
}

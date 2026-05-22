package com.example.knowledgecopilot.retrieval.internal;

import com.example.knowledgecopilot.config.AppProperties;
import com.example.knowledgecopilot.observability.MetricNames;
import com.example.knowledgecopilot.retrieval.api.EmbeddingPort;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class EmbeddingService implements EmbeddingPort {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final AppProperties properties;
    private final MeterRegistry meterRegistry;

    public EmbeddingService(AppProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void logConfiguration() {
        AppProperties.Embedding embedding = properties.getEmbedding();
        log.info(
            "Embedding configuration: model={}, dimensions={}, apiKeyPresent={}",
            embedding.getModel(),
            embedding.getDimensions(),
            embedding.getApiKey() != null && !embedding.getApiKey().isBlank()
        );
    }

    @Override
    public float[] embed(String text) {
        String input = text == null ? "" : text.trim();
        if (input.isBlank()) {
            return new float[dimensions()];
        }

        return embedWithOpenAi(input);
    }

    public double cosineSimilarity(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0) {
            return 0.0d;
        }

        int length = Math.min(left.length, right.length);
        double dot = 0.0d;
        double leftMagnitude = 0.0d;
        double rightMagnitude = 0.0d;
        for (int i = 0; i < length; i++) {
            double leftValue = left[i];
            double rightValue = right[i];
            dot += leftValue * rightValue;
            leftMagnitude += leftValue * leftValue;
            rightMagnitude += rightValue * rightValue;
        }

        if (leftMagnitude == 0.0d || rightMagnitude == 0.0d) {
            return 0.0d;
        }

        return dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }

    private float[] embedWithOpenAi(String input) {
        AppProperties.Embedding embedding = properties.getEmbedding();
        if (embedding.getApiKey() == null || embedding.getApiKey().isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not set.");
        }
        Timer.Sample timerSample = Timer.start(meterRegistry);

        var payload = new java.util.LinkedHashMap<String, Object>();
        payload.put("model", embedding.getModel());
        payload.put("input", input);
        payload.put("encoding_format", "float");
        if (embedding.getDimensions() > 0) {
            payload.put("dimensions", embedding.getDimensions());
        }

        try {
            JsonNode response = RestClient.builder()
                .baseUrl(embedding.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + embedding.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build()
                .post()
                .uri("/embeddings")
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

            if (response == null) {
                throw new IllegalStateException("OpenAI embeddings response was empty.");
            }

            JsonNode embeddingNode = response.path("data").path(0).path("embedding");
            if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
                throw new IllegalStateException("OpenAI embeddings response did not contain an embedding vector.");
            }

            float[] values = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                values[i] = (float) embeddingNode.get(i).asDouble();
            }

            int expectedDimensions = dimensions();
            if (expectedDimensions > 0 && values.length != expectedDimensions) {
                throw new IllegalStateException(
                    "OpenAI returned " + values.length + " dimensions, expected " + expectedDimensions + "."
                );
            }

            recordTokenUsageMetrics(response, embedding.getModel());
            meterRegistry.counter(
                MetricNames.OPENAI_EMBEDDING_REQUESTS_TOTAL,
                "status", "success",
                "model", safeTagValue(embedding.getModel())
            ).increment();
            return values;
        } catch (RuntimeException ex) {
            meterRegistry.counter(
                MetricNames.OPENAI_EMBEDDING_REQUESTS_TOTAL,
                "status", "failure",
                "model", safeTagValue(embedding.getModel()),
                "error", ex.getClass().getSimpleName()
            ).increment();
            throw ex;
        } finally {
            timerSample.stop(
                Timer.builder(MetricNames.OPENAI_EMBEDDING_LATENCY)
                    .tag("model", safeTagValue(embedding.getModel()))
                    .register(meterRegistry)
            );
        }
    }

    private int dimensions() {
        int dimensions = properties.getEmbedding().getDimensions();
        if (dimensions <= 0) {
            throw new IllegalStateException("Embedding dimensions must be greater than zero.");
        }
        return dimensions;
    }

    private void recordTokenUsageMetrics(JsonNode response, String model) {
        JsonNode usage = response.path("usage");
        if (usage.isMissingNode()) {
            return;
        }
        long promptTokens = usage.path("prompt_tokens").asLong(-1);
        if (promptTokens >= 0) {
            DistributionSummary.builder(MetricNames.OPENAI_EMBEDDING_USAGE_TOKENS)
                .baseUnit("tokens")
                .tag("type", "prompt")
                .tag("model", safeTagValue(model))
                .register(meterRegistry)
                .record(promptTokens);
        }
        long totalTokens = usage.path("total_tokens").asLong(-1);
        if (totalTokens >= 0) {
            DistributionSummary.builder(MetricNames.OPENAI_EMBEDDING_USAGE_TOKENS)
                .baseUnit("tokens")
                .tag("type", "total")
                .tag("model", safeTagValue(model))
                .register(meterRegistry)
                .record(totalTokens);
        }
    }

    private String safeTagValue(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}

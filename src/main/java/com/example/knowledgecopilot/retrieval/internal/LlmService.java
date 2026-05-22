package com.example.knowledgecopilot.retrieval.internal;

import com.example.knowledgecopilot.config.AppProperties;
import com.example.knowledgecopilot.entity.KnowledgeChunk;
import com.example.knowledgecopilot.observability.MetricNames;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LlmService {
    private static final Logger log = LoggerFactory.getLogger(LlmService.class);
    private static final String ANSWER_INSTRUCTIONS = """
        You are a knowledge assistant for an indexed document corpus.
        Answer the user's question using only the provided context.
        Cite source labels from the context inline when useful.
        If the context is insufficient, say that the indexed documents do not contain enough information.
    """;

    private final AppProperties properties;
    private final MeterRegistry meterRegistry;

    public LlmService(AppProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void logConfiguration() {
        AppProperties.Llm llm = properties.getLlm();
        log.info(
            "LLM configuration: model={}, apiKeyPresent={}",
            llm.getModel(),
            llm.getApiKey() != null && !llm.getApiKey().isBlank()
        );
    }

    public String answer(String question, String conversationSummary, List<KnowledgeChunk> chunks) {
        if (chunks.isEmpty()) {
            return "I could not find enough information in the indexed documents to answer that question.";
        }

        return answerWithOpenAi(question, conversationSummary, buildContext(chunks));
    }

    private String answerWithOpenAi(String question, String conversationSummary, String context) {
        AppProperties.Llm llm = properties.getLlm();
        if (llm.getApiKey() == null || llm.getApiKey().isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not set.");
        }
        Timer.Sample timerSample = Timer.start(meterRegistry);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", llm.getModel());
        payload.put("instructions", ANSWER_INSTRUCTIONS);
        payload.put(
            "input",
            "Conversation Summary:\n" + safeConversationSummary(conversationSummary)
                + "\n\nQuestion:\n" + question
                + "\n\nContext:\n" + context
        );
        payload.put("max_output_tokens", llm.getMaxOutputTokens());

        try {
            JsonNode response = RestClient.builder()
                .baseUrl(llm.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + llm.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build()
                .post()
                .uri("/responses")
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

            String answer = extractOutputText(response);
            if (answer.isBlank()) {
                throw new IllegalStateException("OpenAI returned no output text.");
            }

            recordUsageMetrics(response, llm.getModel());
            meterRegistry.counter(
                MetricNames.OPENAI_LLM_REQUESTS_TOTAL,
                "status", "success",
                "model", safeTagValue(llm.getModel())
            ).increment();

            return answer;
        } catch (RuntimeException ex) {
            meterRegistry.counter(
                MetricNames.OPENAI_LLM_REQUESTS_TOTAL,
                "status", "failure",
                "model", safeTagValue(llm.getModel()),
                "error", ex.getClass().getSimpleName()
            ).increment();
            throw ex;
        } finally {
            timerSample.stop(
                Timer.builder(MetricNames.OPENAI_LLM_LATENCY)
                    .tag("model", safeTagValue(llm.getModel()))
                    .register(meterRegistry)
            );
        }
    }

    private String buildContext(List<KnowledgeChunk> chunks) {
        return chunks.stream()
            .map(chunk -> {
                var document = chunk.getDocument();
                return "[" + chunk.getCitationLabel() + "]\n"
                    + "sourceType=" + safe(document.getSourceType())
                    + ", sourcePath=" + safe(document.getSourcePath())
                    + ", fileName=" + safe(document.getFileName())
                    + ", title=" + safe(document.getTitle())
                    + ", updatedAt=" + (document.getUpdatedAt() == null ? "" : document.getUpdatedAt())
                    + ", chunkIndex=" + chunk.getChunkIndex()
                    + "\n"
                    + chunk.getContent();
            })
            .collect(Collectors.joining("\n\n"));
    }

    public String getModelUsed() {
        return properties.getLlm().getModel();
    }

    public String getPromptVersion() {
        return properties.getLlm().getPromptVersion();
    }

    private String extractOutputText(JsonNode response) {
        if (response == null) {
            return "";
        }
        JsonNode outputText = response.path("output_text");
        if (outputText.isTextual() && !outputText.asText().isBlank()) {
            return outputText.asText();
        }

        JsonNode output = response.path("output");
        if (!output.isArray()) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode contentItem : content) {
                if ("output_text".equals(contentItem.path("type").asText())) {
                    text.append(contentItem.path("text").asText()).append("\n");
                }
            }
        }
        return text.toString().trim();
    }

    private void recordUsageMetrics(JsonNode response, String model) {
        JsonNode usage = response == null ? null : response.path("usage");
        if (usage == null || usage.isMissingNode()) {
            return;
        }
        recordTokenSummary("input", usage.path("input_tokens").asLong(-1), model);
        recordTokenSummary("output", usage.path("output_tokens").asLong(-1), model);
        recordTokenSummary("total", usage.path("total_tokens").asLong(-1), model);
    }

    private void recordTokenSummary(String tokenType, long value, String model) {
        if (value < 0) {
            return;
        }
        DistributionSummary.builder(MetricNames.OPENAI_LLM_USAGE_TOKENS)
            .baseUnit("tokens")
            .tag("type", tokenType)
            .tag("model", safeTagValue(model))
            .register(meterRegistry)
            .record(value);
    }

    private String safeTagValue(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String safeConversationSummary(String conversationSummary) {
        if (conversationSummary == null || conversationSummary.isBlank()) {
            return "No prior conversation context.";
        }
        return conversationSummary;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

}

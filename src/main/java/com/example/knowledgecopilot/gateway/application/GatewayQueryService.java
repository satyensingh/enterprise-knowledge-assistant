package com.example.knowledgecopilot.gateway.application;

import com.example.knowledgecopilot.dto.AskResponse;
import com.example.knowledgecopilot.dto.AskRequest;
import com.example.knowledgecopilot.entity.AssistantResponseRecord;
import com.example.knowledgecopilot.entity.QuestionCluster;
import com.example.knowledgecopilot.observability.MetricNames;
import com.example.knowledgecopilot.repository.AssistantResponseRecordRepository;
import com.example.knowledgecopilot.retrieval.api.AskExecutionTrace;
import com.example.knowledgecopilot.retrieval.api.RetrievalModule;
import com.example.knowledgecopilot.retrieval.api.RetrievalQuery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GatewayQueryService {
    private static final List<String> INSUFFICIENT_ANSWER_PHRASES = List.of(
        "could not find enough information",
        "do not contain enough information",
        "don't contain enough information",
        "insufficient information"
    );

    private final RetrievalModule retrievalModule;
    private final AssistantResponseRecordRepository responseRecordRepository;
    private final QuestionClusterResolverService questionClusterResolverService;
    private final AuditEventService auditEventService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public GatewayQueryService(
        RetrievalModule retrievalModule,
        AssistantResponseRecordRepository responseRecordRepository,
        QuestionClusterResolverService questionClusterResolverService,
        AuditEventService auditEventService,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.retrievalModule = retrievalModule;
        this.responseRecordRepository = responseRecordRepository;
        this.questionClusterResolverService = questionClusterResolverService;
        this.auditEventService = auditEventService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public AskResponse ask(String question) {
        AskRequest request = new AskRequest();
        request.setQuestion(question);
        return ask(request);
    }

    @Transactional
    public AskResponse ask(AskRequest request) {
        Timer.Sample timerSample = Timer.start(meterRegistry);
        AskRequest safeRequest = request == null ? new AskRequest() : request;
        String normalizedQuestion = safeRequest.getQuestion() == null ? "" : safeRequest.getQuestion().trim();
        String modelTag = "unknown";
        String promptTag = "unknown";
        try {
            String currentUsername = CurrentUserProvider.currentUsername();
            String conversationSummary = buildConversationSummary(currentUsername);
            RetrievalQuery retrievalQuery = toRetrievalQuery(safeRequest, normalizedQuestion);
            AskExecutionTrace trace = retrievalModule.askWithTrace(retrievalQuery, conversationSummary);
            QuestionCluster questionCluster = questionClusterResolverService.resolve(trace.getQuestion());

            AssistantResponseRecord record = new AssistantResponseRecord();
            record.setId(UUID.randomUUID());
            record.setAskedBy(CurrentUserProvider.currentUsername());
            record.setQuestionCluster(questionCluster);
            record.setQuestion(trace.getQuestion());
            record.setGeneratedAnswer(trace.getAnswer());
            record.setCitationsJson(toJson(trace.getCitations()));
            record.setRetrievedChunksJson(toJson(trace.getRetrievedChunks()));
            record.setLatencyMs(trace.getLatencyMs());
            record.setModelUsed(trace.getModelUsed());
            record.setPromptVersion(trace.getPromptVersion());
            record.setCreatedAt(Instant.now());

            responseRecordRepository.save(record);
            modelTag = trace.getModelUsed();
            promptTag = trace.getPromptVersion();

            DistributionSummary.builder(MetricNames.ASK_RESPONSE_CHARS)
                .baseUnit("characters")
                .register(meterRegistry)
                .record(trace.getAnswer() == null ? 0 : trace.getAnswer().length());

            String insufficientReason = detectInsufficientReason(trace);
            if (insufficientReason != null) {
                meterRegistry.counter(
                    MetricNames.ASK_INSUFFICIENT_ANSWERS_TOTAL,
                    "reason", insufficientReason,
                    "model", safeTagValue(modelTag),
                    "prompt_version", safeTagValue(promptTag)
                ).increment();
            }

            meterRegistry.counter(
                MetricNames.ASK_REQUESTS_TOTAL,
                "status", "success",
                "model", safeTagValue(modelTag),
                "prompt_version", safeTagValue(promptTag)
            ).increment();

            boolean insufficientEvidence = Boolean.TRUE.equals(trace.getInsufficientEvidence()) || insufficientReason != null;
            auditEventService.record(
                "ASSISTANT_ASK",
                "assistant_response_record",
                record.getId().toString(),
                200,
                Map.of(
                    "question", normalizedQuestion,
                    "modelUsed", safeTagValue(modelTag),
                    "promptVersion", safeTagValue(promptTag),
                    "insufficientEvidence", insufficientEvidence
                )
            );
            return new AskResponse(record.getId().toString(), trace.getAnswer(), trace.getCitations(), insufficientEvidence);
        } catch (RuntimeException ex) {
            meterRegistry.counter(
                MetricNames.ASK_REQUESTS_TOTAL,
                "status", "failure",
                "model", safeTagValue(modelTag),
                "prompt_version", safeTagValue(promptTag)
            ).increment();
            auditEventService.record(
                "ASSISTANT_ASK",
                "assistant_response_record",
                null,
                500,
                Map.of(
                    "question", normalizedQuestion,
                    "error", ex.getClass().getSimpleName()
                )
            );
            throw ex;
        } finally {
            timerSample.stop(
                Timer.builder(MetricNames.ASK_LATENCY)
                    .tag("model", safeTagValue(modelTag))
                    .tag("prompt_version", safeTagValue(promptTag))
                    .register(meterRegistry)
            );
        }
    }

    private RetrievalQuery toRetrievalQuery(AskRequest request, String normalizedQuestion) {
        return new RetrievalQuery(
            normalizedQuestion,
            normalizeList(request.getSourceTypes()),
            normalizeList(request.getSourcePaths()),
            normalizeList(request.getWorkspaceIds()),
            request.getUpdatedAfter(),
            request.getUpdatedBefore(),
            normalizeMap(request.getMetadataFilters()),
            normalizeMap(request.getConnectorFilters())
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize ask response payload.", ex);
        }
    }

    private String safeTagValue(String value) {
        return (value == null || value.isBlank()) ? "unknown" : value;
    }

    private String buildConversationSummary(String username) {
        if (username == null || username.isBlank() || "anonymous".equalsIgnoreCase(username)) {
            return "No prior conversation context.";
        }
        List<AssistantResponseRecord> recentResponses = responseRecordRepository
            .findTop5ByAskedByOrderByCreatedAtDesc(username);
        if (recentResponses == null || recentResponses.isEmpty()) {
            return "No prior conversation context.";
        }

        List<AssistantResponseRecord> chronological = recentResponses.stream()
            .sorted((left, right) -> left.getCreatedAt().compareTo(right.getCreatedAt()))
            .toList();

        return chronological.stream()
            .map(record -> "User: " + trimForSummary(record.getQuestion(), 220)
                + "\nAssistant: " + trimForSummary(record.getGeneratedAnswer(), 320))
            .collect(Collectors.joining("\n\n"));
    }

    private String trimForSummary(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...";
    }

    private String detectInsufficientReason(AskExecutionTrace trace) {
        if (trace == null) {
            return "unknown";
        }

        if (trace.getRetrievedChunks() == null || trace.getRetrievedChunks().isEmpty()) {
            return "no_retrieved_chunks";
        }

        long authorizedChunkCount = trace.getRetrievedChunks().stream()
            .filter(chunk -> chunk != null && chunk.isAuthorized())
            .count();
        if (authorizedChunkCount == 0L) {
            return "no_authorized_chunks";
        }

        String answer = trace.getAnswer();
        if (answer == null || answer.isBlank()) {
            return "blank_answer";
        }

        String normalizedAnswer = answer.toLowerCase(Locale.ROOT);
        for (String phrase : INSUFFICIENT_ANSWER_PHRASES) {
            if (normalizedAnswer.contains(phrase)) {
                return "insufficient_phrase";
            }
        }
        return null;
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
    }

    private Map<String, String> normalizeMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return values.entrySet().stream()
            .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
            .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
            .collect(Collectors.toMap(
                entry -> entry.getKey().trim(),
                entry -> entry.getValue().trim(),
                (left, right) -> right
            ));
    }
}

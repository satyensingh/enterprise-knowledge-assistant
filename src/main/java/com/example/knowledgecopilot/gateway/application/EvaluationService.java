package com.example.knowledgecopilot.gateway.application;

import com.example.knowledgecopilot.dto.*;
import com.example.knowledgecopilot.entity.EvaluationRun;
import com.example.knowledgecopilot.entity.FeedbackVerdict;
import com.example.knowledgecopilot.entity.GoldenQuestion;
import com.example.knowledgecopilot.entity.QuestionCluster;
import com.example.knowledgecopilot.observability.MetricNames;
import com.example.knowledgecopilot.repository.AssistantResponseFeedbackRepository;
import com.example.knowledgecopilot.repository.EvaluationRunRepository;
import com.example.knowledgecopilot.repository.GoldenQuestionRepository;
import com.example.knowledgecopilot.retrieval.api.AskExecutionTrace;
import com.example.knowledgecopilot.retrieval.api.RetrievalModule;
import com.example.knowledgecopilot.retrieval.api.RetrievedChunkTrace;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EvaluationService {
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<RetrievedChunkTrace>> RETRIEVED_CHUNK_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<CitationDto>> CITATION_LIST_TYPE = new TypeReference<>() {};

    private final GoldenQuestionRepository goldenQuestionRepository;
    private final EvaluationRunRepository evaluationRunRepository;
    private final AssistantResponseFeedbackRepository assistantResponseFeedbackRepository;
    private final QuestionClusterResolverService questionClusterResolverService;
    private final RetrievalModule retrievalModule;
    private final AuditEventService auditEventService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public EvaluationService(
        GoldenQuestionRepository goldenQuestionRepository,
        EvaluationRunRepository evaluationRunRepository,
        AssistantResponseFeedbackRepository assistantResponseFeedbackRepository,
        QuestionClusterResolverService questionClusterResolverService,
        RetrievalModule retrievalModule,
        AuditEventService auditEventService,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry
    ) {
        this.goldenQuestionRepository = goldenQuestionRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.assistantResponseFeedbackRepository = assistantResponseFeedbackRepository;
        this.questionClusterResolverService = questionClusterResolverService;
        this.retrievalModule = retrievalModule;
        this.auditEventService = auditEventService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public GoldenQuestionDto createGoldenQuestion(CreateGoldenQuestionRequest request) {
        Instant now = Instant.now();

        GoldenQuestion goldenQuestion = new GoldenQuestion();
        goldenQuestion.setId(UUID.randomUUID());
        goldenQuestion.setQuestion(request.getQuestion().trim());
        goldenQuestion.setExpectedSourcesJson(toJson(normalizeExpectedSources(request.getExpectedSources())));
        goldenQuestion.setCreatedAt(now);
        goldenQuestion.setUpdatedAt(now);

        GoldenQuestion saved = goldenQuestionRepository.save(goldenQuestion);
        auditEventService.record(
            "EVAL_GOLDEN_CREATE",
            "golden_question",
            saved.getId().toString(),
            201,
            Map.of("question", saved.getQuestion())
        );
        return toGoldenQuestionDto(saved);
    }

    @Transactional(readOnly = true)
    public List<GoldenQuestionDto> listGoldenQuestions() {
        List<GoldenQuestionDto> results = goldenQuestionRepository.findAllByOrderByCreatedAtDesc()
            .stream()
            .map(this::toGoldenQuestionDto)
            .toList();
        auditEventService.record(
            "EVAL_GOLDEN_LIST",
            "golden_question",
            null,
            200,
            Map.of("count", results.size())
        );
        return results;
    }

    @Transactional
    public EvaluationRunDto runEvaluation(UUID goldenQuestionId) {
        Timer.Sample timerSample = Timer.start(meterRegistry);
        String modelTag = "unknown";
        String promptTag = "unknown";
        try {
            GoldenQuestion goldenQuestion = goldenQuestionRepository.findById(goldenQuestionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Golden question not found."));
            List<String> expectedSources = fromJson(goldenQuestion.getExpectedSourcesJson(), STRING_LIST_TYPE);
            AskExecutionTrace trace = retrievalModule.askWithTrace(goldenQuestion.getQuestion());
            QuestionCluster questionCluster = questionClusterResolverService.resolve(goldenQuestion.getQuestion());
            modelTag = trace.getModelUsed();
            promptTag = trace.getPromptVersion();

            Set<String> citedSourcePaths = trace.getCitations() == null
                ? Set.of()
                : trace.getCitations().stream()
                    .map(CitationDto::getSourcePath)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(path -> !path.isBlank())
                    .collect(Collectors.toSet());

            long matchedExpectedSources = expectedSources.stream()
                .filter(citedSourcePaths::contains)
                .count();

            double citationCorrectness = expectedSources.isEmpty()
                ? 1.0d
                : (double) matchedExpectedSources / (double) expectedSources.size();
            long goodFeedbackCount = assistantResponseFeedbackRepository
                .countByResponseRecordQuestionClusterIdAndVerdict(questionCluster.getId(), FeedbackVerdict.GOOD);
            long badFeedbackCount = assistantResponseFeedbackRepository
                .countByResponseRecordQuestionClusterIdAndVerdict(questionCluster.getId(), FeedbackVerdict.BAD);
            long totalFeedbackCount = goodFeedbackCount + badFeedbackCount;
            Double goodFeedbackRate = totalFeedbackCount == 0L
                ? null
                : (double) goodFeedbackCount / (double) totalFeedbackCount;

            EvaluationRun run = new EvaluationRun();
            run.setId(UUID.randomUUID());
            run.setGoldenQuestion(goldenQuestion);
            run.setQuestion(trace.getQuestion());
            run.setExpectedSourcesJson(toJson(expectedSources));
            run.setRetrievedChunksJson(toJson(trace.getRetrievedChunks()));
            run.setCitationsJson(toJson(trace.getCitations()));
            run.setGeneratedAnswer(trace.getAnswer());
            run.setCitationCorrectness(citationCorrectness);
            run.setExpectedSourceCount(expectedSources.size());
            run.setMatchedExpectedSourceCount(Math.toIntExact(matchedExpectedSources));
            run.setQuestionFeedbackGoodCount(Math.toIntExact(goodFeedbackCount));
            run.setQuestionFeedbackBadCount(Math.toIntExact(badFeedbackCount));
            run.setQuestionFeedbackGoodRate(goodFeedbackRate);
            run.setLatencyMs(trace.getLatencyMs());
            run.setModelUsed(trace.getModelUsed());
            run.setPromptVersion(trace.getPromptVersion());
            run.setCreatedAt(Instant.now());

            DistributionSummary.builder(MetricNames.EVALUATION_CITATION_CORRECTNESS)
                .register(meterRegistry)
                .record(citationCorrectness);
            DistributionSummary.builder(MetricNames.EVALUATION_EXPECTED_SOURCES)
                .baseUnit("sources")
                .register(meterRegistry)
                .record(expectedSources.size());
            DistributionSummary.builder(MetricNames.EVALUATION_MATCHED_SOURCES)
                .baseUnit("sources")
                .register(meterRegistry)
                .record(matchedExpectedSources);

            meterRegistry.counter(
                MetricNames.EVALUATION_RUNS_TOTAL,
                "status", "success",
                "model", safeTagValue(modelTag),
                "prompt_version", safeTagValue(promptTag)
            ).increment();
            EvaluationRun saved = evaluationRunRepository.save(run);
            auditEventService.record(
                "EVAL_RUN_EXECUTE",
                "evaluation_run",
                saved.getId().toString(),
                201,
                Map.of(
                    "goldenQuestionId", goldenQuestionId.toString(),
                    "citationCorrectness", citationCorrectness,
                    "expectedSourceCount", expectedSources.size(),
                    "matchedExpectedSourceCount", Math.toIntExact(matchedExpectedSources)
                )
            );
            return toEvaluationRunDto(saved);
        } catch (ResponseStatusException ex) {
            auditEventService.record(
                "EVAL_RUN_EXECUTE",
                "evaluation_run",
                null,
                ex.getStatusCode().value(),
                Map.of(
                    "goldenQuestionId", goldenQuestionId.toString(),
                    "error", ex.getReason() == null ? ex.getClass().getSimpleName() : ex.getReason()
                )
            );
            throw ex;
        } catch (RuntimeException ex) {
            meterRegistry.counter(
                MetricNames.EVALUATION_RUNS_TOTAL,
                "status", "failure",
                "model", safeTagValue(modelTag),
                "prompt_version", safeTagValue(promptTag),
                "error", ex.getClass().getSimpleName()
            ).increment();
            auditEventService.record(
                "EVAL_RUN_EXECUTE",
                "evaluation_run",
                null,
                500,
                Map.of(
                    "goldenQuestionId", goldenQuestionId.toString(),
                    "error", ex.getClass().getSimpleName()
                )
            );
            throw ex;
        } finally {
            timerSample.stop(
                Timer.builder(MetricNames.EVALUATION_LATENCY)
                    .tag("model", safeTagValue(modelTag))
                    .tag("prompt_version", safeTagValue(promptTag))
                    .register(meterRegistry)
            );
        }
    }

    @Transactional(readOnly = true)
    public List<EvaluationRunDto> listRuns(UUID goldenQuestionId) {
        if (goldenQuestionId == null) {
            List<EvaluationRunDto> runs = evaluationRunRepository.findTop50ByOrderByCreatedAtDesc()
                .stream()
                .map(this::toEvaluationRunDto)
                .toList();
            auditEventService.record(
                "EVAL_RUN_LIST",
                "evaluation_run",
                null,
                200,
                Map.of("count", runs.size(), "filtered", false)
            );
            return runs;
        }

        List<EvaluationRunDto> runs = evaluationRunRepository.findByGoldenQuestionIdOrderByCreatedAtDesc(goldenQuestionId)
            .stream()
            .map(this::toEvaluationRunDto)
            .toList();
        auditEventService.record(
            "EVAL_RUN_LIST",
            "evaluation_run",
            null,
            200,
            Map.of("count", runs.size(), "filtered", true, "goldenQuestionId", goldenQuestionId.toString())
        );
        return runs;
    }

    @Transactional
    public EvaluationRunDto saveFeedback(UUID runId, EvaluationFeedbackRequest request) {
        EvaluationRun run;
        try {
            run = evaluationRunRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evaluation run not found."));
        } catch (ResponseStatusException ex) {
            auditEventService.record(
                "EVAL_RUN_FEEDBACK",
                "evaluation_run",
                runId.toString(),
                ex.getStatusCode().value(),
                Map.of("error", ex.getReason() == null ? ex.getClass().getSimpleName() : ex.getReason())
            );
            throw ex;
        }

        run.setUserFeedbackScore(request.getScore());
        run.setUserFeedbackComment(request.getComment());
        if (request.getScore() != null) {
            DistributionSummary.builder(MetricNames.FEEDBACK_SCORE)
                .baseUnit("score")
                .tag("source", "evaluation_run")
                .register(meterRegistry)
                .record(request.getScore());
        }
        EvaluationRun saved = evaluationRunRepository.save(run);
        auditEventService.record(
            "EVAL_RUN_FEEDBACK",
            "evaluation_run",
            runId.toString(),
            200,
            Map.of("score", request.getScore(), "hasComment", request.getComment() != null && !request.getComment().isBlank())
        );
        return toEvaluationRunDto(saved);
    }

    private GoldenQuestionDto toGoldenQuestionDto(GoldenQuestion goldenQuestion) {
        return new GoldenQuestionDto(
            goldenQuestion.getId().toString(),
            goldenQuestion.getQuestion(),
            fromJson(goldenQuestion.getExpectedSourcesJson(), STRING_LIST_TYPE),
            goldenQuestion.getCreatedAt(),
            goldenQuestion.getUpdatedAt()
        );
    }

    private EvaluationRunDto toEvaluationRunDto(EvaluationRun run) {
        EvaluationRunDto dto = new EvaluationRunDto();
        dto.setId(run.getId().toString());
        dto.setGoldenQuestionId(run.getGoldenQuestion().getId().toString());
        dto.setGoldenQuestion(run.getQuestion());
        dto.setExpectedSources(fromJson(run.getExpectedSourcesJson(), STRING_LIST_TYPE));
        dto.setRetrievedChunks(fromJson(run.getRetrievedChunksJson(), RETRIEVED_CHUNK_LIST_TYPE));
        dto.setCitations(fromJson(run.getCitationsJson(), CITATION_LIST_TYPE));
        dto.setGeneratedAnswer(run.getGeneratedAnswer());
        dto.setCitationCorrectness(run.getCitationCorrectness());
        dto.setExpectedSourceCount(run.getExpectedSourceCount());
        dto.setMatchedExpectedSourceCount(run.getMatchedExpectedSourceCount());
        dto.setUserFeedbackScore(run.getUserFeedbackScore());
        dto.setUserFeedbackComment(run.getUserFeedbackComment());
        dto.setQuestionFeedbackGoodCount(run.getQuestionFeedbackGoodCount());
        dto.setQuestionFeedbackBadCount(run.getQuestionFeedbackBadCount());
        dto.setQuestionFeedbackGoodRate(run.getQuestionFeedbackGoodRate());
        dto.setLatencyMs(run.getLatencyMs());
        dto.setModelUsed(run.getModelUsed());
        dto.setPromptVersion(run.getPromptVersion());
        dto.setCreatedAt(run.getCreatedAt());
        return dto;
    }

    private List<String> normalizeExpectedSources(List<String> expectedSources) {
        if (expectedSources == null) {
            return List.of();
        }
        return expectedSources.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize evaluation payload.", ex);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeReference) {
        if (json == null || json.isBlank()) {
            try {
                return objectMapper.readValue("[]", typeReference);
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Failed to deserialize evaluation payload.", ex);
            }
        }

        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize evaluation payload.", ex);
        }
    }

    private String safeTagValue(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}

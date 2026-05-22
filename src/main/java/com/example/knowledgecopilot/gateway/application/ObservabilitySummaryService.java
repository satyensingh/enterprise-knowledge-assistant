package com.example.knowledgecopilot.gateway.application;

import com.example.knowledgecopilot.entity.EvaluationRun;
import com.example.knowledgecopilot.entity.FeedbackVerdict;
import com.example.knowledgecopilot.observability.MetricNames;
import com.example.knowledgecopilot.repository.AssistantResponseFeedbackRepository;
import com.example.knowledgecopilot.repository.AssistantResponseRecordRepository;
import com.example.knowledgecopilot.repository.EvaluationRunRepository;
import com.example.knowledgecopilot.repository.GoldenQuestionRepository;
import com.example.knowledgecopilot.repository.KnowledgeChunkRepository;
import com.example.knowledgecopilot.repository.KnowledgeDocumentRepository;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ObservabilitySummaryService {
    private final MeterRegistry meterRegistry;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final GoldenQuestionRepository goldenQuestionRepository;
    private final EvaluationRunRepository evaluationRunRepository;
    private final AssistantResponseRecordRepository responseRecordRepository;
    private final AssistantResponseFeedbackRepository feedbackRepository;

    public ObservabilitySummaryService(
        MeterRegistry meterRegistry,
        KnowledgeDocumentRepository documentRepository,
        KnowledgeChunkRepository chunkRepository,
        GoldenQuestionRepository goldenQuestionRepository,
        EvaluationRunRepository evaluationRunRepository,
        AssistantResponseRecordRepository responseRecordRepository,
        AssistantResponseFeedbackRepository feedbackRepository
    ) {
        this.meterRegistry = meterRegistry;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.goldenQuestionRepository = goldenQuestionRepository;
        this.evaluationRunRepository = evaluationRunRepository;
        this.responseRecordRepository = responseRecordRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildSummary() {
        List<EvaluationRun> recentRuns = evaluationRunRepository.findTop200ByOrderByCreatedAtDesc();

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generatedAt", Instant.now().toString());

        Map<String, Object> inventory = new LinkedHashMap<>();
        inventory.put("documentsIndexed", documentRepository.count());
        inventory.put("chunksIndexed", chunkRepository.count());
        inventory.put("goldenQuestions", goldenQuestionRepository.count());
        inventory.put("evaluationRuns", evaluationRunRepository.count());
        inventory.put("assistantResponses", responseRecordRepository.count());
        inventory.put("feedbackGood", feedbackRepository.countByVerdict(FeedbackVerdict.GOOD));
        inventory.put("feedbackBad", feedbackRepository.countByVerdict(FeedbackVerdict.BAD));
        root.put("inventory", inventory);

        Map<String, Object> askMetrics = new LinkedHashMap<>();
        askMetrics.put("totalSuccess", counterSumByTags(MetricNames.ASK_REQUESTS_TOTAL, "status", "success"));
        askMetrics.put("totalFailure", counterSumByTags(MetricNames.ASK_REQUESTS_TOTAL, "status", "failure"));
        askMetrics.put("askLatencyCount", timerCount(MetricNames.ASK_LATENCY));
        askMetrics.put("askLatencyTotalMs", timerTotalMs(MetricNames.ASK_LATENCY));
        root.put("ask", askMetrics);

        Map<String, Object> evalMetrics = new LinkedHashMap<>();
        evalMetrics.put("totalSuccess", counterSumByTags(MetricNames.EVALUATION_RUNS_TOTAL, "status", "success"));
        evalMetrics.put("totalFailure", counterSumByTags(MetricNames.EVALUATION_RUNS_TOTAL, "status", "failure"));
        evalMetrics.put("avgCitationCorrectnessRecent200", averageCitationCorrectness(recentRuns));
        evalMetrics.put("avgLatencyMsRecent200", averageLatencyMs(recentRuns));
        evalMetrics.put("modelBreakdownRecent200", modelBreakdown(recentRuns));
        evalMetrics.put("promptBreakdownRecent200", promptBreakdown(recentRuns));
        root.put("evaluation", evalMetrics);

        Map<String, Object> securityMetrics = new LinkedHashMap<>();
        securityMetrics.put("auth401Total", counterSumByTags(MetricNames.SECURITY_AUTH_FAILURES_TOTAL, "status", "401"));
        securityMetrics.put("auth403Total", counterSumByTags(MetricNames.SECURITY_AUTH_FAILURES_TOTAL, "status", "403"));
        securityMetrics.put("rateLimitRejectedTotal", counterSumByTags(MetricNames.SECURITY_RATE_LIMIT_REJECTIONS_TOTAL));
        root.put("security", securityMetrics);

        Map<String, Object> ingestionMetrics = new LinkedHashMap<>();
        ingestionMetrics.put("reindexSuccessTotal", counterSumByTags(MetricNames.INGESTION_REINDEX_TOTAL, "status", "success"));
        ingestionMetrics.put("reindexFailureTotal", counterSumByTags(MetricNames.INGESTION_REINDEX_TOTAL, "status", "failure"));
        ingestionMetrics.put("reindexLatencyCount", timerCount(MetricNames.INGESTION_REINDEX_LATENCY));
        ingestionMetrics.put("reindexLatencyTotalMs", timerTotalMs(MetricNames.INGESTION_REINDEX_LATENCY));
        root.put("ingestion", ingestionMetrics);

        Map<String, Object> feedbackMetrics = new LinkedHashMap<>();
        feedbackMetrics.put("goodSubmissionsTotal", counterSumByTags(MetricNames.FEEDBACK_SUBMISSIONS_TOTAL, "verdict", "GOOD"));
        feedbackMetrics.put("badSubmissionsTotal", counterSumByTags(MetricNames.FEEDBACK_SUBMISSIONS_TOTAL, "verdict", "BAD"));
        root.put("feedback", feedbackMetrics);

        Map<String, Object> auditMetrics = new LinkedHashMap<>();
        auditMetrics.put("totalEvents", counterSumByTags(MetricNames.AUDIT_EVENTS_TOTAL));
        root.put("audit", auditMetrics);
        return root;
    }

    private double averageCitationCorrectness(List<EvaluationRun> runs) {
        return runs.stream()
            .map(EvaluationRun::getCitationCorrectness)
            .filter(value -> value != null)
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0d);
    }

    private double averageLatencyMs(List<EvaluationRun> runs) {
        return runs.stream()
            .map(EvaluationRun::getLatencyMs)
            .filter(value -> value != null)
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0d);
    }

    private Map<String, Long> modelBreakdown(List<EvaluationRun> runs) {
        return runs.stream()
            .map(EvaluationRun::getModelUsed)
            .collect(Collectors.groupingBy(this::safeValue, Collectors.counting()));
    }

    private Map<String, Long> promptBreakdown(List<EvaluationRun> runs) {
        return runs.stream()
            .map(EvaluationRun::getPromptVersion)
            .collect(Collectors.groupingBy(this::safeValue, Collectors.counting()));
    }

    private double counterSumByTags(String name, String... tags) {
        Map<String, String> requiredTags = toTagMap(tags);
        return meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getName().equals(name))
            .filter(meter -> containsAllTags(meter, requiredTags))
            .mapToDouble(this::countMeasurementValue)
            .sum();
    }

    private long timerCount(String name) {
        return meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getName().equals(name))
            .mapToLong(this::timerCountMeasurementValue)
            .sum();
    }

    private double timerTotalMs(String name) {
        double totalSeconds = meterRegistry.getMeters().stream()
            .filter(meter -> meter.getId().getName().equals(name))
            .mapToDouble(this::timerTotalSecondsMeasurementValue)
            .sum();
        return totalSeconds * 1000.0d;
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private Map<String, String> toTagMap(String... tags) {
        Map<String, String> map = new LinkedHashMap<>();
        if (tags == null) {
            return map;
        }
        for (int i = 0; i < tags.length - 1; i = i + 2) {
            map.put(tags[i], tags[i + 1]);
        }
        return map;
    }

    private boolean containsAllTags(Meter meter, Map<String, String> requiredTags) {
        if (requiredTags.isEmpty()) {
            return true;
        }
        Map<String, String> presentTags = meter.getId().getTags().stream()
            .collect(Collectors.toMap(tag -> tag.getKey(), tag -> tag.getValue(), (left, right) -> left));
        return requiredTags.entrySet().stream()
            .allMatch(entry -> entry.getValue().equals(presentTags.get(entry.getKey())));
    }

    private double countMeasurementValue(Meter meter) {
        double sum = 0.0d;
        for (Measurement measurement : meter.measure()) {
            if (measurement.getStatistic() == io.micrometer.core.instrument.Statistic.COUNT) {
                sum += measurement.getValue();
            }
        }
        return sum;
    }

    private long timerCountMeasurementValue(Meter meter) {
        for (Measurement measurement : meter.measure()) {
            if (measurement.getStatistic() == io.micrometer.core.instrument.Statistic.COUNT) {
                return (long) measurement.getValue();
            }
        }
        return 0L;
    }

    private double timerTotalSecondsMeasurementValue(Meter meter) {
        for (Measurement measurement : meter.measure()) {
            if (measurement.getStatistic() == io.micrometer.core.instrument.Statistic.TOTAL_TIME) {
                return measurement.getValue();
            }
        }
        return 0.0d;
    }
}

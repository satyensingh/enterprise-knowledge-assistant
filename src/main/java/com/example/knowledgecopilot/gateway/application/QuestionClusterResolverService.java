package com.example.knowledgecopilot.gateway.application;

import com.example.knowledgecopilot.config.AppProperties;
import com.example.knowledgecopilot.entity.QuestionCluster;
import com.example.knowledgecopilot.observability.MetricNames;
import com.example.knowledgecopilot.repository.QuestionClusterRepository;
import com.example.knowledgecopilot.retrieval.api.EmbeddingPort;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class QuestionClusterResolverService {
    private static final String EMPTY_QUESTION_TOKEN = "__empty_question__";

    private final QuestionClusterRepository questionClusterRepository;
    private final EmbeddingPort embeddingPort;
    private final AppProperties properties;
    private final MeterRegistry meterRegistry;

    public QuestionClusterResolverService(
        QuestionClusterRepository questionClusterRepository,
        EmbeddingPort embeddingPort,
        AppProperties properties,
        MeterRegistry meterRegistry
    ) {
        this.questionClusterRepository = questionClusterRepository;
        this.embeddingPort = embeddingPort;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public QuestionCluster resolve(String question) {
        String rawQuestion = question == null ? "" : question.trim();
        String normalizedQuestion = normalizeQuestion(rawQuestion);

        var exact = questionClusterRepository.findByNormalizedQuestion(normalizedQuestion);
        if (exact.isPresent()) {
            recordResolution("exact");
            return exact.get();
        }

        float[] embedding = embedOrZeroVector(normalizedQuestion);
        SimilarClusterResolution similarResolution = resolveSimilarCluster(embedding);
        QuestionCluster similar = similarResolution.cluster();
        if (similar != null) {
            recordResolution("similar");
            DistributionSummary.builder(MetricNames.QUESTION_CLUSTER_SIMILARITY)
                .register(meterRegistry)
                .record(similarResolution.similarityScore());
            return similar;
        }

        Instant now = Instant.now();
        QuestionCluster cluster = new QuestionCluster();
        cluster.setId(UUID.randomUUID());
        cluster.setNormalizedQuestion(normalizedQuestion);
        cluster.setRepresentativeQuestion(rawQuestion.isBlank() ? normalizedQuestion : rawQuestion);
        cluster.setEmbedding(embedding);
        cluster.setCreatedAt(now);
        cluster.setUpdatedAt(now);

        try {
            QuestionCluster created = questionClusterRepository.save(cluster);
            recordResolution("new");
            return created;
        } catch (DataIntegrityViolationException ex) {
            QuestionCluster existing = questionClusterRepository.findByNormalizedQuestion(normalizedQuestion)
                .orElseThrow(() -> ex);
            recordResolution("exact");
            return existing;
        }
    }

    private SimilarClusterResolution resolveSimilarCluster(float[] embedding) {
        int candidatePool = Math.max(1, properties.getEvaluation().getQuestionClusterCandidatePoolSize());
        double threshold = properties.getEvaluation().getQuestionClusterSimilarityThreshold();
        List<QuestionCluster> candidates = new ArrayList<>();
        questionClusterRepository.findAll().forEach(candidates::add);
        candidates = candidates.stream()
            .sorted(Comparator.comparingDouble(
                (QuestionCluster cluster) -> cosineSimilarity(embedding, cluster.getEmbedding())
            ).reversed())
            .limit(candidatePool)
            .toList();

        QuestionCluster bestCluster = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (QuestionCluster candidate : candidates) {
            double score = cosineSimilarity(embedding, candidate.getEmbedding());
            if (score > bestScore) {
                bestScore = score;
                bestCluster = candidate;
            }
        }

        if (bestCluster == null || bestScore < threshold) {
            return new SimilarClusterResolution(null, bestScore);
        }
        return new SimilarClusterResolution(bestCluster, bestScore);
    }

    private String normalizeQuestion(String question) {
        if (question == null || question.isBlank()) {
            return EMPTY_QUESTION_TOKEN;
        }

        String normalized = question
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();

        return normalized.isBlank() ? EMPTY_QUESTION_TOKEN : normalized;
    }

    private float[] embedOrZeroVector(String normalizedQuestion) {
        try {
            return embeddingPort.embed(normalizedQuestion);
        } catch (RuntimeException ex) {
            int dimensions = Math.max(1, properties.getEmbedding().getDimensions());
            return new float[dimensions];
        }
    }

    private double cosineSimilarity(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || right.length == 0) {
            return 0.0d;
        }
        int size = Math.min(left.length, right.length);
        double dot = 0.0d;
        double leftMagnitude = 0.0d;
        double rightMagnitude = 0.0d;
        for (int i = 0; i < size; i++) {
            double l = left[i];
            double r = right[i];
            dot += l * r;
            leftMagnitude += l * l;
            rightMagnitude += r * r;
        }
        if (leftMagnitude == 0.0d || rightMagnitude == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }

    private void recordResolution(String resolutionType) {
        meterRegistry.counter(
            MetricNames.QUESTION_CLUSTER_RESOLUTION_TOTAL,
            "type",
            resolutionType
        ).increment();
    }

    private record SimilarClusterResolution(QuestionCluster cluster, double similarityScore) {}
}

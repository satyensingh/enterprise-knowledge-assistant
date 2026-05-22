package com.example.knowledgecopilot.retrieval.internal;

import com.example.knowledgecopilot.config.AppProperties;
import com.example.knowledgecopilot.entity.KnowledgeChunk;
import com.example.knowledgecopilot.observability.MetricNames;
import com.example.knowledgecopilot.repository.KnowledgeChunkRepository;
import com.example.knowledgecopilot.retrieval.api.RetrievalQuery;
import com.example.knowledgecopilot.retrieval.api.VectorSearchMatch;
import com.example.knowledgecopilot.retrieval.api.VectorStorePort;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RetrievalService {
    private final KnowledgeChunkRepository chunkRepository;
    private final VectorStorePort vectorStore;
    private final EmbeddingService embeddingService;
    private final RetrievalFilterService retrievalFilterService;
    private final AppProperties properties;
    private final MeterRegistry meterRegistry;

    public RetrievalService(
        KnowledgeChunkRepository chunkRepository,
        VectorStorePort vectorStore,
        EmbeddingService embeddingService,
        RetrievalFilterService retrievalFilterService,
        AppProperties properties,
        MeterRegistry meterRegistry
    ) {
        this.chunkRepository = chunkRepository;
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.retrievalFilterService = retrievalFilterService;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    public List<KnowledgeChunk> retrieve(String question) {
        RetrievalQuery query = new RetrievalQuery();
        query.setQuestion(question);
        return retrieve(query);
    }

    public List<KnowledgeChunk> retrieve(RetrievalQuery query) {
        return retrieveCandidates(query);
    }

    public List<KnowledgeChunk> retrieveCandidates(RetrievalQuery query) {
        Timer.Sample timerSample = Timer.start(meterRegistry);
        int retrieved = 0;
        try {
            RetrievalQuery safeQuery = sanitizeQuery(query);
            QueryUnderstandingResult understanding = understand(safeQuery.getQuestion());
            float[] queryEmbedding = embeddingService.embed(understanding.normalizedQuestion());
            Set<String> queryTerms = understanding.queryTerms();
            int candidatePoolSize = Math.max(
                properties.getRetrieval().getTopK(),
                properties.getRetrieval().getCandidatePoolSize()
            );

            List<VectorSearchMatch> vectorMatches = vectorStore.query(queryEmbedding, candidatePoolSize);
            List<UUID> vectorIds = vectorMatches.stream()
                .map(VectorSearchMatch::id)
                .map(this::safeUuid)
                .filter(Objects::nonNull)
                .toList();
            List<KnowledgeChunk> vectorCandidates = orderByIds(
                chunkRepository.findByIdIn(vectorIds),
                vectorIds
            );

            List<KnowledgeChunk> filteredVectorCandidates = retrievalFilterService.applyFilters(vectorCandidates, safeQuery);

            int keywordLimitPerTerm = Math.max(
                2,
                candidatePoolSize / Math.max(1, understanding.keywordTerms().size())
            );
            List<KnowledgeChunk> keywordCandidates = understanding.keywordTerms().stream()
                .flatMap(term -> chunkRepository.findKeywordCandidates(term, PageRequest.of(0, keywordLimitPerTerm)).stream())
                .toList();

            List<KnowledgeChunk> filteredKeywordCandidates = retrievalFilterService.applyFilters(keywordCandidates, safeQuery);
            List<KnowledgeChunk> mergedCandidates = mergeCandidates(filteredVectorCandidates, filteredKeywordCandidates);

            retrieved = mergedCandidates.size();
            DistributionSummary.builder(MetricNames.RETRIEVAL_CHUNKS)
                .baseUnit("chunks")
                .tags("stage", "vector_candidates")
                .register(meterRegistry)
                .record(vectorCandidates.size());
            DistributionSummary.builder(MetricNames.RETRIEVAL_CHUNKS)
                .baseUnit("chunks")
                .tags("stage", "keyword_candidates")
                .register(meterRegistry)
                .record(keywordCandidates.size());
            DistributionSummary.builder(MetricNames.RETRIEVAL_CHUNKS)
                .baseUnit("chunks")
                .tags("stage", "filtered_candidates")
                .register(meterRegistry)
                .record(mergedCandidates.size());
            DistributionSummary.builder(MetricNames.RETRIEVAL_CHUNKS)
                .baseUnit("chunks")
                .tags("stage", "candidate_pool")
                .register(meterRegistry)
                .record(candidatePoolSize);

            return mergedCandidates;
        } finally {
            timerSample.stop(
                Timer.builder(MetricNames.RETRIEVAL_LATENCY)
                    .tag("result", retrieved > 0 ? "hits" : "empty")
                    .register(meterRegistry)
            );
        }
    }

    public List<KnowledgeChunk> rerankAndLimit(String question, List<KnowledgeChunk> candidates, int limit) {
        if (candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }

        QueryUnderstandingResult understanding = understand(question);
        float[] queryEmbedding = embeddingService.embed(understanding.normalizedQuestion());
        Set<String> queryTerms = understanding.queryTerms();

        List<KnowledgeChunk> reranked = candidates.stream()
            .map(chunk -> Map.entry(chunk, score(chunk, queryEmbedding, queryTerms)))
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(limit)
            .map(Map.Entry::getKey)
            .toList();

        DistributionSummary.builder(MetricNames.RETRIEVAL_CHUNKS)
            .baseUnit("chunks")
            .tags("stage", "top_k")
            .register(meterRegistry)
            .record(reranked.size());

        return reranked;
    }

    public int topK() {
        return Math.max(1, properties.getRetrieval().getTopK());
    }

    private List<KnowledgeChunk> mergeCandidates(List<KnowledgeChunk> vectorCandidates, List<KnowledgeChunk> keywordCandidates) {
        LinkedHashMap<UUID, KnowledgeChunk> merged = new LinkedHashMap<>();
        for (KnowledgeChunk chunk : vectorCandidates) {
            merged.put(chunk.getId(), chunk);
        }
        for (KnowledgeChunk chunk : keywordCandidates) {
            merged.putIfAbsent(chunk.getId(), chunk);
        }
        return merged.values().stream()
                .toList();
    }

    private RetrievalQuery sanitizeQuery(RetrievalQuery query) {
        RetrievalQuery safe = query == null ? new RetrievalQuery() : query;
        if (safe.getQuestion() == null) {
            safe.setQuestion("");
        }
        return safe;
    }

    private QueryUnderstandingResult understand(String rawQuestion) {
        String normalizedQuestion = rawQuestion == null ? "" : rawQuestion.trim().replaceAll("\\s+", " ");
        Set<String> queryTerms = normalizeTerms(normalizedQuestion);
        List<String> keywordTerms = queryTerms.stream()
            .limit(5)
            .toList();
        if (keywordTerms.isEmpty() && !normalizedQuestion.isBlank()) {
            keywordTerms = List.of(normalizedQuestion);
        }
        return new QueryUnderstandingResult(normalizedQuestion, queryTerms, keywordTerms);
    }

    private double score(KnowledgeChunk chunk, float[] queryEmbedding, Set<String> queryTerms) {
        double vectorScore = embeddingService.cosineSimilarity(queryEmbedding, chunk.getEmbedding());
        double lexicalScore = lexicalScore(chunk, queryTerms);

        return (properties.getRetrieval().getVectorWeight() * vectorScore)
            + (properties.getRetrieval().getLexicalWeight() * lexicalScore);
    }

    private double lexicalScore(KnowledgeChunk chunk, Set<String> queryTerms) {
        if (queryTerms.isEmpty()) {
            return 0.0d;
        }

        Set<String> chunkTerms = normalizeTerms(
            chunk.getContent() + " " +
                chunk.getDocument().getTitle() + " " +
                chunk.getDocument().getFileName()
        );

        if (chunkTerms.isEmpty()) {
            return 0.0d;
        }

        long matches = queryTerms.stream()
            .filter(chunkTerms::contains)
            .count();

        return (double) matches / (double) queryTerms.size();
    }

    private Set<String> normalizeTerms(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .split("\\s+"))
            .filter(token -> !token.isBlank())
            .filter(token -> token.length() > 2 || token.chars().allMatch(Character::isDigit))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private record QueryUnderstandingResult(
        String normalizedQuestion,
        Set<String> queryTerms,
        List<String> keywordTerms
    ) {}

    private List<KnowledgeChunk> orderByIds(List<KnowledgeChunk> chunks, List<UUID> orderedIds) {
        if (chunks == null || chunks.isEmpty() || orderedIds == null || orderedIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, KnowledgeChunk> byId = chunks.stream()
            .collect(Collectors.toMap(KnowledgeChunk::getId, chunk -> chunk, (left, right) -> left));
        List<KnowledgeChunk> ordered = new ArrayList<>();
        for (UUID id : orderedIds) {
            KnowledgeChunk chunk = byId.get(id);
            if (chunk != null) {
                ordered.add(chunk);
            }
        }
        return ordered;
    }

    private UUID safeUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

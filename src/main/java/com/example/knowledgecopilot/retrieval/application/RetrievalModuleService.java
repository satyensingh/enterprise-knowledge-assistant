package com.example.knowledgecopilot.retrieval.application;

import com.example.knowledgecopilot.dto.AskResponse;
import com.example.knowledgecopilot.entity.KnowledgeChunk;
import com.example.knowledgecopilot.observability.MetricNames;
import com.example.knowledgecopilot.retrieval.api.AskExecutionTrace;
import com.example.knowledgecopilot.retrieval.api.RetrievedChunkTrace;
import com.example.knowledgecopilot.retrieval.api.RetrievalModule;
import com.example.knowledgecopilot.retrieval.api.RetrievalQuery;
import com.example.knowledgecopilot.retrieval.internal.AuthorizationFilterService;
import com.example.knowledgecopilot.retrieval.internal.CitationBuilderService;
import com.example.knowledgecopilot.retrieval.internal.LlmService;
import com.example.knowledgecopilot.retrieval.internal.RetrievalService;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RetrievalModuleService implements RetrievalModule {
    private final RetrievalService retrievalService;
    private final AuthorizationFilterService authorizationFilterService;
    private final CitationBuilderService citationBuilderService;
    private final LlmService llmService;
    private final MeterRegistry meterRegistry;

    public RetrievalModuleService(
        RetrievalService retrievalService,
        AuthorizationFilterService authorizationFilterService,
        CitationBuilderService citationBuilderService,
        LlmService llmService,
        MeterRegistry meterRegistry
    ) {
        this.retrievalService = retrievalService;
        this.authorizationFilterService = authorizationFilterService;
        this.citationBuilderService = citationBuilderService;
        this.llmService = llmService;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Transactional(readOnly = true)
    public AskResponse ask(String question) {
        RetrievalQuery query = new RetrievalQuery();
        query.setQuestion(question);
        return askWithTrace(query, null).toAskResponse();
    }

    @Override
    @Transactional(readOnly = true)
    public AskExecutionTrace askWithTrace(String question) {
        RetrievalQuery query = new RetrievalQuery();
        query.setQuestion(question);
        return askWithTrace(query, null);
    }

    @Override
    @Transactional(readOnly = true)
    public AskExecutionTrace askWithTrace(String question, String conversationSummary) {
        RetrievalQuery query = new RetrievalQuery();
        query.setQuestion(question);
        return askWithTrace(query, conversationSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public AskExecutionTrace askWithTrace(RetrievalQuery query) {
        return askWithTrace(query, null);
    }

    @Override
    @Transactional(readOnly = true)
    public AskExecutionTrace askWithTrace(RetrievalQuery query, String conversationSummary) {
        long startedAt = System.nanoTime();
        RetrievalQuery safeQuery = query == null ? new RetrievalQuery() : query;
        String question = safeQuery.getQuestion() == null ? "" : safeQuery.getQuestion();

        List<KnowledgeChunk> retrievedChunks = retrievalService.retrieveCandidates(safeQuery);
        List<KnowledgeChunk> authorizedCandidates = authorizationFilterService.filterAuthorizedChunks(retrievedChunks);
        List<KnowledgeChunk> authorizedChunks = retrievalService.rerankAndLimit(
            question,
            authorizedCandidates,
            retrievalService.topK()
        );
        String answer = llmService.answer(question, conversationSummary, authorizedChunks);

        var citations = authorizedChunks.stream()
            .map(citationBuilderService::buildCitation)
            .distinct()
            .toList();

        DistributionSummary.builder(MetricNames.RETRIEVAL_AUTHORIZED_CHUNKS)
            .baseUnit("chunks")
            .register(meterRegistry)
            .record(authorizedChunks.size());
        DistributionSummary.builder(MetricNames.RETRIEVAL_CITATIONS)
            .baseUnit("citations")
            .register(meterRegistry)
            .record(citations.size());

        Set<String> authorizedChunkIds = authorizedChunks.stream()
            .map(chunk -> chunk.getId().toString())
            .collect(Collectors.toSet());

        List<RetrievedChunkTrace> chunkTraces = retrievedChunks.stream()
            .map(chunk -> toChunkTrace(chunk, authorizedChunkIds))
            .toList();

        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000L;
        boolean insufficientEvidence = isInsufficientEvidence(retrievedChunks, authorizedChunks, answer);
        return new AskExecutionTrace(
            question,
            answer,
            citations,
            chunkTraces,
            latencyMs,
            llmService.getModelUsed(),
            llmService.getPromptVersion(),
            insufficientEvidence
        );
    }

    private String excerpt(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        int max = 320;
        if (content.length() <= max) {
            return content;
        }
        return content.substring(0, max) + "...";
    }

    private RetrievedChunkTrace toChunkTrace(KnowledgeChunk chunk, Set<String> authorizedChunkIds) {
        boolean authorized = authorizedChunkIds.contains(chunk.getId().toString());
        return new RetrievedChunkTrace(
            chunk.getId().toString(),
            chunk.getDocument().getId().toString(),
            chunk.getDocument().getSourcePath(),
            chunk.getDocument().getFileName(),
            chunk.getChunkIndex(),
            chunk.getCitationLabel(),
            authorized ? excerpt(chunk.getContent()) : "",
            authorized
        );
    }

    private boolean isInsufficientEvidence(
        List<KnowledgeChunk> retrievedChunks,
        List<KnowledgeChunk> authorizedChunks,
        String answer
    ) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return true;
        }
        if (authorizedChunks == null || authorizedChunks.isEmpty()) {
            return true;
        }
        if (answer == null || answer.isBlank()) {
            return true;
        }
        String normalizedAnswer = answer.toLowerCase(Locale.ROOT);
        return normalizedAnswer.contains("could not find enough information")
            || normalizedAnswer.contains("do not contain enough information")
            || normalizedAnswer.contains("don't contain enough information")
            || normalizedAnswer.contains("insufficient information");
    }
}

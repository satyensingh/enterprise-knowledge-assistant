package com.example.knowledgecopilot.retrieval.internal;

import com.example.knowledgecopilot.config.AppProperties;
import com.example.knowledgecopilot.entity.KnowledgeChunk;
import com.example.knowledgecopilot.entity.KnowledgeDocument;
import com.example.knowledgecopilot.repository.KnowledgeChunkRepository;
import com.example.knowledgecopilot.retrieval.api.RetrievalQuery;
import com.example.knowledgecopilot.retrieval.api.VectorSearchMatch;
import com.example.knowledgecopilot.retrieval.api.VectorStorePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RetrievalServiceTest {

    @Test
    void reranksVectorCandidatesUsingLexicalOverlap() {
        AppProperties properties = new AppProperties();
        properties.getRetrieval().setTopK(2);
        properties.getRetrieval().setCandidatePoolSize(2);
        properties.getRetrieval().setVectorWeight(0.8d);
        properties.getRetrieval().setLexicalWeight(0.2d);

        KnowledgeChunkRepository chunkRepository = mock(KnowledgeChunkRepository.class);
        VectorStorePort vectorStore = mock(VectorStorePort.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EmbeddingService embeddingService = spy(new EmbeddingService(new AppProperties(), meterRegistry));
        RetrievalFilterService retrievalFilterService = new RetrievalFilterService(new ObjectMapper());
        RetrievalService retrievalService = new RetrievalService(
            chunkRepository,
            vectorStore,
            embeddingService,
            retrievalFilterService,
            properties,
            meterRegistry
        );

        float[] queryEmbedding = new float[] {1.0f, 0.0f};
        doReturn(queryEmbedding).when(embeddingService).embed("find the contract renewal clause");

        KnowledgeChunk vectorFirst = chunk(
            "miscellaneous overview",
            new float[] {0.9f, 0.4358899f}
        );
        KnowledgeChunk lexicalFirst = chunk(
            "the contract renewal clause",
            new float[] {0.85f, 0.5267827f}
        );

        when(vectorStore.query(eq(queryEmbedding), eq(2))).thenReturn(List.of(
            new VectorSearchMatch(vectorFirst.getId().toString(), 0.1d),
            new VectorSearchMatch(lexicalFirst.getId().toString(), 0.2d)
        ));
        when(chunkRepository.findByIdIn(any()))
            .thenReturn(List.of(vectorFirst, lexicalFirst));
        when(chunkRepository.findKeywordCandidates(anyString(), any()))
            .thenReturn(List.of());

        RetrievalQuery query = new RetrievalQuery();
        query.setQuestion("find the contract renewal clause");
        List<KnowledgeChunk> candidates = retrievalService.retrieveCandidates(query);
        List<KnowledgeChunk> results = retrievalService.rerankAndLimit(query.getQuestion(), candidates, 1);

        assertEquals(1, results.size());
        assertEquals(lexicalFirst, results.get(0));
        verify(vectorStore).query(eq(queryEmbedding), eq(2));
    }

    @Test
    void requestsConfiguredCandidatePoolSize() {
        AppProperties properties = new AppProperties();
        properties.getRetrieval().setTopK(5);
        properties.getRetrieval().setCandidatePoolSize(12);

        KnowledgeChunkRepository chunkRepository = mock(KnowledgeChunkRepository.class);
        VectorStorePort vectorStore = mock(VectorStorePort.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EmbeddingService embeddingService = spy(new EmbeddingService(new AppProperties(), meterRegistry));
        RetrievalFilterService retrievalFilterService = new RetrievalFilterService(new ObjectMapper());
        RetrievalService retrievalService = new RetrievalService(
            chunkRepository,
            vectorStore,
            embeddingService,
            retrievalFilterService,
            properties,
            meterRegistry
        );

        float[] queryEmbedding = new float[] {1.0f, 0.0f};
        doReturn(queryEmbedding).when(embeddingService).embed("question");
        when(vectorStore.query(eq(queryEmbedding), eq(12))).thenReturn(List.of());
        when(chunkRepository.findByIdIn(any())).thenReturn(List.of());
        when(chunkRepository.findKeywordCandidates(anyString(), any()))
            .thenReturn(List.of());

        RetrievalQuery query = new RetrievalQuery();
        query.setQuestion("question");
        retrievalService.retrieveCandidates(query);

        verify(vectorStore).query(eq(queryEmbedding), eq(12));
    }

    @Test
    void appliesSourceTypeFilterBeforeHybridMerge() {
        AppProperties properties = new AppProperties();
        properties.getRetrieval().setTopK(5);
        properties.getRetrieval().setCandidatePoolSize(10);

        KnowledgeChunkRepository chunkRepository = mock(KnowledgeChunkRepository.class);
        VectorStorePort vectorStore = mock(VectorStorePort.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EmbeddingService embeddingService = spy(new EmbeddingService(new AppProperties(), meterRegistry));
        RetrievalFilterService retrievalFilterService = new RetrievalFilterService(new ObjectMapper());
        RetrievalService retrievalService = new RetrievalService(
            chunkRepository,
            vectorStore,
            embeddingService,
            retrievalFilterService,
            properties,
            meterRegistry
        );

        float[] queryEmbedding = new float[] {1.0f, 0.0f};
        doReturn(queryEmbedding).when(embeddingService).embed("ingestion flow");

        KnowledgeChunk jiraChunk = chunk("jira content", "JIRA");
        KnowledgeChunk fileChunk = chunk("file content", "LOCAL_FILE_SYSTEM");

        when(vectorStore.query(eq(queryEmbedding), eq(10))).thenReturn(List.of(
            new VectorSearchMatch(jiraChunk.getId().toString(), 0.1d),
            new VectorSearchMatch(fileChunk.getId().toString(), 0.2d)
        ));
        when(chunkRepository.findByIdIn(any())).thenReturn(List.of(jiraChunk, fileChunk));
        when(chunkRepository.findKeywordCandidates(anyString(), any()))
            .thenReturn(List.of(fileChunk));

        RetrievalQuery query = new RetrievalQuery();
        query.setQuestion("ingestion flow");
        query.setSourceTypes(List.of("JIRA"));

        List<KnowledgeChunk> candidates = retrievalService.retrieveCandidates(query);

        assertEquals(1, candidates.size());
        assertEquals("JIRA", candidates.get(0).getDocument().getSourceType());
    }

    private KnowledgeChunk chunk(String content, float[] embedding) {
        return chunk(content, "FILE_SYSTEM", embedding);
    }

    private KnowledgeChunk chunk(String content, String sourceType) {
        return chunk(content, sourceType, new float[] {0.8f, 0.6f});
    }

    private KnowledgeChunk chunk(String content, String sourceType, float[] embedding) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(UUID.randomUUID());
        document.setSourceType(sourceType);
        document.setSourcePath("/tmp/doc.txt");
        document.setFileName("doc.txt");
        document.setTitle("Doc");
        document.setContentType("text/plain");
        document.setChecksum("checksum");
        document.setUpdatedAt(Instant.parse("2026-05-14T00:00:00Z"));
        document.setIndexedAt(Instant.parse("2026-05-14T00:00:00Z"));
        document.setMetadataJson("{}");

        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(UUID.randomUUID());
        chunk.setDocument(document);
        chunk.setChunkIndex(0);
        chunk.setContent(content);
        chunk.setEmbedding(embedding);
        chunk.setCitationLabel("doc.txt#chunk-0");
        chunk.setMetadataJson("{}");
        return chunk;
    }
}

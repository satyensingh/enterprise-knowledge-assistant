package com.example.knowledgecopilot.retrieval.application;

import com.example.knowledgecopilot.entity.KnowledgeChunk;
import com.example.knowledgecopilot.entity.KnowledgeDocument;
import com.example.knowledgecopilot.retrieval.api.RetrievalQuery;
import com.example.knowledgecopilot.retrieval.internal.AuthorizationFilterService;
import com.example.knowledgecopilot.retrieval.internal.CitationBuilderService;
import com.example.knowledgecopilot.retrieval.internal.LlmService;
import com.example.knowledgecopilot.retrieval.internal.RetrievalService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RetrievalModuleServiceTest {

    @Test
    void appliesAclBeforeRerankAndLlmContextAssembly() {
        RetrievalService retrievalService = mock(RetrievalService.class);
        AuthorizationFilterService authorizationFilterService = mock(AuthorizationFilterService.class);
        CitationBuilderService citationBuilderService = spy(new CitationBuilderService());
        LlmService llmService = mock(LlmService.class);
        RetrievalModuleService service = new RetrievalModuleService(
            retrievalService,
            authorizationFilterService,
            citationBuilderService,
            llmService,
            new SimpleMeterRegistry()
        );

        KnowledgeChunk candidateA = chunk("a#0", 0);
        KnowledgeChunk candidateB = chunk("b#1", 1);
        RetrievalQuery query = new RetrievalQuery();
        query.setQuestion("How does ingestion flow work?");

        when(retrievalService.retrieveCandidates(query)).thenReturn(List.of(candidateA, candidateB));
        when(authorizationFilterService.filterAuthorizedChunks(List.of(candidateA, candidateB)))
            .thenReturn(List.of(candidateB));
        when(retrievalService.topK()).thenReturn(5);
        when(retrievalService.rerankAndLimit(query.getQuestion(), List.of(candidateB), 5))
            .thenReturn(List.of(candidateB));
        when(llmService.answer(query.getQuestion(), "summary", List.of(candidateB))).thenReturn("ok");
        when(llmService.getModelUsed()).thenReturn("gpt-4.1-mini");
        when(llmService.getPromptVersion()).thenReturn("v1");

        var trace = service.askWithTrace(query, "summary");

        assertEquals("ok", trace.getAnswer());
        assertEquals(1, trace.getCitations().size());
        assertEquals(2, trace.getRetrievedChunks().size());
        assertEquals(false, trace.getRetrievedChunks().get(0).isAuthorized());
        assertEquals(true, trace.getRetrievedChunks().get(1).isAuthorized());

        verify(llmService).answer(query.getQuestion(), "summary", List.of(candidateB));
        InOrder inOrder = inOrder(retrievalService, authorizationFilterService, llmService);
        inOrder.verify(retrievalService).retrieveCandidates(query);
        inOrder.verify(authorizationFilterService).filterAuthorizedChunks(List.of(candidateA, candidateB));
        inOrder.verify(retrievalService).rerankAndLimit(query.getQuestion(), List.of(candidateB), 5);
        inOrder.verify(llmService).answer(query.getQuestion(), "summary", List.of(candidateB));
        verify(llmService).getModelUsed();
        verify(llmService).getPromptVersion();
    }

    private KnowledgeChunk chunk(String citationLabel, int chunkIndex) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(UUID.randomUUID());
        document.setSourceType("LOCAL_FILE_SYSTEM");
        document.setSourcePath("/tmp/doc-" + chunkIndex + ".txt");
        document.setFileName("doc-" + chunkIndex + ".txt");
        document.setTitle("Doc " + chunkIndex);
        document.setContentType("text/plain");
        document.setChecksum("checksum-" + chunkIndex);
        document.setUpdatedAt(Instant.parse("2026-05-14T00:00:00Z"));
        document.setIndexedAt(Instant.parse("2026-05-14T00:00:00Z"));
        document.setMetadataJson("{}");

        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(UUID.randomUUID());
        chunk.setDocument(document);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent("content " + chunkIndex);
        chunk.setEmbedding(new float[] {1.0f, 0.0f});
        chunk.setCitationLabel(citationLabel);
        chunk.setMetadataJson("{}");
        return chunk;
    }
}

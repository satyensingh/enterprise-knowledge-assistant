package com.example.knowledgecopilot.gateway.application;

import com.example.knowledgecopilot.dto.CitationDto;
import com.example.knowledgecopilot.dto.CreateGoldenQuestionRequest;
import com.example.knowledgecopilot.dto.EvaluationRunDto;
import com.example.knowledgecopilot.entity.FeedbackVerdict;
import com.example.knowledgecopilot.entity.GoldenQuestion;
import com.example.knowledgecopilot.entity.QuestionCluster;
import com.example.knowledgecopilot.repository.AssistantResponseFeedbackRepository;
import com.example.knowledgecopilot.repository.EvaluationRunRepository;
import com.example.knowledgecopilot.repository.GoldenQuestionRepository;
import com.example.knowledgecopilot.retrieval.api.AskExecutionTrace;
import com.example.knowledgecopilot.retrieval.api.RetrievalModule;
import com.example.knowledgecopilot.retrieval.api.RetrievedChunkTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EvaluationServiceTest {

    @Test
    void runEvaluationComputesCitationCorrectnessFromExpectedSources() {
        GoldenQuestionRepository goldenQuestionRepository = mock(GoldenQuestionRepository.class);
        EvaluationRunRepository evaluationRunRepository = mock(EvaluationRunRepository.class);
        AssistantResponseFeedbackRepository feedbackRepository = mock(AssistantResponseFeedbackRepository.class);
        QuestionClusterResolverService clusterResolverService = mock(QuestionClusterResolverService.class);
        RetrievalModule retrievalModule = mock(RetrievalModule.class);
        AuditEventService auditEventService = mock(AuditEventService.class);
        EvaluationService service = new EvaluationService(
            goldenQuestionRepository,
            evaluationRunRepository,
            feedbackRepository,
            clusterResolverService,
            retrievalModule,
            auditEventService,
            new ObjectMapper(),
            new SimpleMeterRegistry()
        );

        UUID goldenId = UUID.randomUUID();
        GoldenQuestion goldenQuestion = new GoldenQuestion();
        goldenQuestion.setId(goldenId);
        goldenQuestion.setQuestion("How is auth done?");
        goldenQuestion.setExpectedSourcesJson("[\"/doc/a.md\",\"/doc/b.md\"]");
        goldenQuestion.setCreatedAt(Instant.now());
        goldenQuestion.setUpdatedAt(Instant.now());

        AskExecutionTrace trace = new AskExecutionTrace(
            "How is auth done?",
            "Auth is Keycloak based.",
            List.of(
                new CitationDto("1", "a.md", "A", "/doc/a.md", "FILE", "text/markdown", "x", null, null, 0, "a#0", "x"),
                new CitationDto("2", "c.md", "C", "/doc/c.md", "FILE", "text/markdown", "y", null, null, 1, "c#1", "y")
            ),
            List.of(new RetrievedChunkTrace("chunk-1", "doc-1", "/doc/a.md", "a.md", 0, "a#0", "x", true)),
            42L,
            "gpt-4.1-mini",
            "v1"
        );

        QuestionCluster questionCluster = new QuestionCluster();
        questionCluster.setId(UUID.randomUUID());

        when(goldenQuestionRepository.findById(goldenId)).thenReturn(Optional.of(goldenQuestion));
        when(retrievalModule.askWithTrace("How is auth done?")).thenReturn(trace);
        when(clusterResolverService.resolve("How is auth done?")).thenReturn(questionCluster);
        when(feedbackRepository.countByResponseRecordQuestionClusterIdAndVerdict(questionCluster.getId(), FeedbackVerdict.GOOD)).thenReturn(8L);
        when(feedbackRepository.countByResponseRecordQuestionClusterIdAndVerdict(questionCluster.getId(), FeedbackVerdict.BAD)).thenReturn(2L);
        when(evaluationRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EvaluationRunDto run = service.runEvaluation(goldenId);

        assertEquals(0.5d, run.getCitationCorrectness());
        assertEquals(2, run.getExpectedSourceCount());
        assertEquals(1, run.getMatchedExpectedSourceCount());
        assertEquals("gpt-4.1-mini", run.getModelUsed());
        assertEquals("v1", run.getPromptVersion());
        assertEquals(8, run.getQuestionFeedbackGoodCount());
        assertEquals(2, run.getQuestionFeedbackBadCount());
        assertEquals(0.8d, run.getQuestionFeedbackGoodRate());
    }

    @Test
    void runEvaluationDefaultsCitationCorrectnessToOneWhenNoExpectedSources() {
        GoldenQuestionRepository goldenQuestionRepository = mock(GoldenQuestionRepository.class);
        EvaluationRunRepository evaluationRunRepository = mock(EvaluationRunRepository.class);
        AssistantResponseFeedbackRepository feedbackRepository = mock(AssistantResponseFeedbackRepository.class);
        QuestionClusterResolverService clusterResolverService = mock(QuestionClusterResolverService.class);
        RetrievalModule retrievalModule = mock(RetrievalModule.class);
        AuditEventService auditEventService = mock(AuditEventService.class);
        EvaluationService service = new EvaluationService(
            goldenQuestionRepository,
            evaluationRunRepository,
            feedbackRepository,
            clusterResolverService,
            retrievalModule,
            auditEventService,
            new ObjectMapper(),
            new SimpleMeterRegistry()
        );

        UUID goldenId = UUID.randomUUID();
        GoldenQuestion goldenQuestion = new GoldenQuestion();
        goldenQuestion.setId(goldenId);
        goldenQuestion.setQuestion("How is indexing done?");
        goldenQuestion.setExpectedSourcesJson("[]");
        goldenQuestion.setCreatedAt(Instant.now());
        goldenQuestion.setUpdatedAt(Instant.now());

        AskExecutionTrace trace = new AskExecutionTrace(
            "How is indexing done?",
            "By scheduled reindexing.",
            List.of(),
            List.of(),
            21L,
            "gpt-4.1-mini",
            "v1"
        );

        QuestionCluster questionCluster = new QuestionCluster();
        questionCluster.setId(UUID.randomUUID());

        when(goldenQuestionRepository.findById(goldenId)).thenReturn(Optional.of(goldenQuestion));
        when(retrievalModule.askWithTrace("How is indexing done?")).thenReturn(trace);
        when(clusterResolverService.resolve("How is indexing done?")).thenReturn(questionCluster);
        when(feedbackRepository.countByResponseRecordQuestionClusterIdAndVerdict(questionCluster.getId(), FeedbackVerdict.GOOD)).thenReturn(0L);
        when(feedbackRepository.countByResponseRecordQuestionClusterIdAndVerdict(questionCluster.getId(), FeedbackVerdict.BAD)).thenReturn(0L);
        when(evaluationRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EvaluationRunDto run = service.runEvaluation(goldenId);
        assertEquals(1.0d, run.getCitationCorrectness());
        assertEquals(0, run.getExpectedSourceCount());
        assertEquals(0, run.getMatchedExpectedSourceCount());
        assertEquals(0, run.getQuestionFeedbackGoodCount());
        assertEquals(0, run.getQuestionFeedbackBadCount());
        assertNull(run.getQuestionFeedbackGoodRate());
    }

    @Test
    void createGoldenQuestionNormalizesExpectedSources() {
        GoldenQuestionRepository goldenQuestionRepository = mock(GoldenQuestionRepository.class);
        EvaluationRunRepository evaluationRunRepository = mock(EvaluationRunRepository.class);
        AssistantResponseFeedbackRepository feedbackRepository = mock(AssistantResponseFeedbackRepository.class);
        QuestionClusterResolverService clusterResolverService = mock(QuestionClusterResolverService.class);
        RetrievalModule retrievalModule = mock(RetrievalModule.class);
        AuditEventService auditEventService = mock(AuditEventService.class);
        EvaluationService service = new EvaluationService(
            goldenQuestionRepository,
            evaluationRunRepository,
            feedbackRepository,
            clusterResolverService,
            retrievalModule,
            auditEventService,
            new ObjectMapper(),
            new SimpleMeterRegistry()
        );

        CreateGoldenQuestionRequest request = new CreateGoldenQuestionRequest();
        request.setQuestion("  How does retrieval work? ");
        request.setExpectedSources(List.of(" /doc/a.md ", "/doc/a.md", "", "   ", "/doc/b.md"));

        when(goldenQuestionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = service.createGoldenQuestion(request);
        assertEquals("How does retrieval work?", dto.getQuestion());
        assertEquals(List.of("/doc/a.md", "/doc/b.md"), dto.getExpectedSources());
    }
}

package com.example.knowledgecopilot.gateway.application;

import com.example.knowledgecopilot.config.AppProperties;
import com.example.knowledgecopilot.entity.QuestionCluster;
import com.example.knowledgecopilot.repository.QuestionClusterRepository;
import com.example.knowledgecopilot.retrieval.api.EmbeddingPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuestionClusterResolverServiceTest {

    @Test
    void reusesExactNormalizedCluster() {
        AppProperties properties = new AppProperties();
        QuestionClusterRepository clusterRepository = mock(QuestionClusterRepository.class);
        EmbeddingPort embeddingPort = mock(EmbeddingPort.class);
        QuestionClusterResolverService service = new QuestionClusterResolverService(
            clusterRepository,
            embeddingPort,
            properties,
            new SimpleMeterRegistry()
        );

        QuestionCluster cluster = new QuestionCluster();
        cluster.setId(UUID.randomUUID());
        cluster.setNormalizedQuestion("how does ingestion flow work");

        when(clusterRepository.findByNormalizedQuestion("how does ingestion flow work")).thenReturn(Optional.of(cluster));

        QuestionCluster resolved = service.resolve("How does ingestion flow work?");

        assertEquals(cluster.getId(), resolved.getId());
        verifyNoInteractions(embeddingPort);
    }

    @Test
    void reusesMostSimilarClusterAboveThreshold() {
        AppProperties properties = new AppProperties();
        properties.getEvaluation().setQuestionClusterSimilarityThreshold(0.80d);
        properties.getEvaluation().setQuestionClusterCandidatePoolSize(5);

        QuestionClusterRepository clusterRepository = mock(QuestionClusterRepository.class);
        EmbeddingPort embeddingPort = mock(EmbeddingPort.class);
        QuestionClusterResolverService service = new QuestionClusterResolverService(
            clusterRepository,
            embeddingPort,
            properties,
            new SimpleMeterRegistry()
        );

        float[] queryEmbedding = new float[]{1.0f, 0.0f};
        QuestionCluster similar = new QuestionCluster();
        similar.setId(UUID.randomUUID());
        similar.setEmbedding(new float[]{0.95f, 0.1f});

        when(clusterRepository.findByNormalizedQuestion("how to reindex now")).thenReturn(Optional.empty());
        when(embeddingPort.embed("how to reindex now")).thenReturn(queryEmbedding);
        when(clusterRepository.findAll()).thenReturn(List.of(similar));

        QuestionCluster resolved = service.resolve("How to reindex now?");
        assertEquals(similar.getId(), resolved.getId());
        verify(clusterRepository, never()).save(any());
    }

    @Test
    void createsNewClusterWhenNoSimilarClusterFound() {
        AppProperties properties = new AppProperties();
        properties.getEvaluation().setQuestionClusterSimilarityThreshold(0.99d);

        QuestionClusterRepository clusterRepository = mock(QuestionClusterRepository.class);
        EmbeddingPort embeddingPort = mock(EmbeddingPort.class);
        QuestionClusterResolverService service = new QuestionClusterResolverService(
            clusterRepository,
            embeddingPort,
            properties,
            new SimpleMeterRegistry()
        );

        float[] queryEmbedding = new float[]{1.0f, 0.0f};

        when(clusterRepository.findByNormalizedQuestion("how does rag work")).thenReturn(Optional.empty());
        when(embeddingPort.embed("how does rag work")).thenReturn(queryEmbedding);
        when(clusterRepository.findAll()).thenReturn(List.of());
        when(clusterRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        QuestionCluster resolved = service.resolve("How does RAG work?");
        assertEquals("how does rag work", resolved.getNormalizedQuestion());
    }
}

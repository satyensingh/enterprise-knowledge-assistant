package com.example.knowledgecopilot.repository;

import com.example.knowledgecopilot.entity.EvaluationRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvaluationRunRepository extends JpaRepository<EvaluationRun, UUID> {
    @EntityGraph(attributePaths = "goldenQuestion")
    List<EvaluationRun> findByGoldenQuestionIdOrderByCreatedAtDesc(UUID goldenQuestionId);

    @EntityGraph(attributePaths = "goldenQuestion")
    List<EvaluationRun> findTop50ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "goldenQuestion")
    List<EvaluationRun> findTop200ByOrderByCreatedAtDesc();
}

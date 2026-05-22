package com.example.knowledgecopilot.repository;

import com.example.knowledgecopilot.entity.QuestionCluster;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuestionClusterRepository extends CrudRepository<QuestionCluster, UUID> {
    Optional<QuestionCluster> findByNormalizedQuestion(String normalizedQuestion);
}

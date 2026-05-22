package com.example.knowledgecopilot.repository;

import com.example.knowledgecopilot.entity.GoldenQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GoldenQuestionRepository extends JpaRepository<GoldenQuestion, UUID> {
    List<GoldenQuestion> findAllByOrderByCreatedAtDesc();
}

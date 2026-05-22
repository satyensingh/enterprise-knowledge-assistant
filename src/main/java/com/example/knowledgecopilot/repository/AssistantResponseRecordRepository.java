package com.example.knowledgecopilot.repository;

import com.example.knowledgecopilot.entity.AssistantResponseRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AssistantResponseRecordRepository extends JpaRepository<AssistantResponseRecord, UUID> {
    List<AssistantResponseRecord> findTop200ByOrderByCreatedAtDesc();
    List<AssistantResponseRecord> findTop5ByAskedByOrderByCreatedAtDesc(String askedBy);
}

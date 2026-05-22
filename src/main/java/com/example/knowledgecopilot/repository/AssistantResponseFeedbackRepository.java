package com.example.knowledgecopilot.repository;

import com.example.knowledgecopilot.entity.AssistantResponseFeedback;
import com.example.knowledgecopilot.entity.FeedbackVerdict;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssistantResponseFeedbackRepository extends JpaRepository<AssistantResponseFeedback, UUID> {
    Optional<AssistantResponseFeedback> findByResponseRecordIdAndSubmittedBy(UUID responseRecordId, String submittedBy);

    long countByResponseRecordQuestionClusterIdAndVerdict(UUID responseRecordQuestionClusterId, FeedbackVerdict verdict);

    long countByResponseRecordIdAndVerdict(UUID responseRecordId, FeedbackVerdict verdict);

    long countByVerdict(FeedbackVerdict verdict);
}

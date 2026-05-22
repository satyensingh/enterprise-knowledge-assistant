package com.example.knowledgecopilot.gateway.application;

import com.example.knowledgecopilot.dto.AnswerFeedbackRequest;
import com.example.knowledgecopilot.dto.AnswerFeedbackResponse;
import com.example.knowledgecopilot.entity.AssistantResponseFeedback;
import com.example.knowledgecopilot.entity.AssistantResponseRecord;
import com.example.knowledgecopilot.entity.FeedbackVerdict;
import com.example.knowledgecopilot.repository.AssistantResponseFeedbackRepository;
import com.example.knowledgecopilot.repository.AssistantResponseRecordRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnswerFeedbackServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitFeedbackCreatesOrUpdatesVoteAndReturnsAggregates() {
        AssistantResponseRecordRepository responseRecordRepository = mock(AssistantResponseRecordRepository.class);
        AssistantResponseFeedbackRepository feedbackRepository = mock(AssistantResponseFeedbackRepository.class);
        AuditEventService auditEventService = mock(AuditEventService.class);
        AnswerFeedbackService service = new AnswerFeedbackService(
            responseRecordRepository,
            feedbackRepository,
            auditEventService,
            new SimpleMeterRegistry()
        );

        UUID responseId = UUID.randomUUID();
        AssistantResponseRecord responseRecord = new AssistantResponseRecord();
        responseRecord.setId(responseId);
        responseRecord.setQuestion("How does retrieval work?");
        responseRecord.setCreatedAt(Instant.now());

        AnswerFeedbackRequest request = new AnswerFeedbackRequest();
        request.setResponseId(responseId);
        request.setVerdict(FeedbackVerdict.GOOD);
        request.setComment("Accurate answer");

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("alice", "n/a")
        );

        when(responseRecordRepository.findById(responseId)).thenReturn(Optional.of(responseRecord));
        when(feedbackRepository.findByResponseRecordIdAndSubmittedBy(responseId, "alice")).thenReturn(Optional.empty());
        when(feedbackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(feedbackRepository.countByResponseRecordIdAndVerdict(responseId, FeedbackVerdict.GOOD)).thenReturn(5L);
        when(feedbackRepository.countByResponseRecordIdAndVerdict(responseId, FeedbackVerdict.BAD)).thenReturn(1L);

        AnswerFeedbackResponse response = service.submitFeedback(request);

        assertEquals(responseId.toString(), response.getResponseId());
        assertEquals(FeedbackVerdict.GOOD, response.getUserVerdict());
        assertEquals(5L, response.getGoodCount());
        assertEquals(1L, response.getBadCount());
        assertEquals(5.0d / 6.0d, response.getGoodRate());
        verify(feedbackRepository).save(any(AssistantResponseFeedback.class));
    }
}

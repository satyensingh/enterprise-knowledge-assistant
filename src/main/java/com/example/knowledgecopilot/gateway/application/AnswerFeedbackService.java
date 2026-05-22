package com.example.knowledgecopilot.gateway.application;

import com.example.knowledgecopilot.dto.AnswerFeedbackRequest;
import com.example.knowledgecopilot.dto.AnswerFeedbackResponse;
import com.example.knowledgecopilot.entity.AssistantResponseFeedback;
import com.example.knowledgecopilot.entity.AssistantResponseRecord;
import com.example.knowledgecopilot.entity.FeedbackVerdict;
import com.example.knowledgecopilot.observability.MetricNames;
import com.example.knowledgecopilot.repository.AssistantResponseFeedbackRepository;
import com.example.knowledgecopilot.repository.AssistantResponseRecordRepository;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class AnswerFeedbackService {
    private final AssistantResponseRecordRepository responseRecordRepository;
    private final AssistantResponseFeedbackRepository feedbackRepository;
    private final AuditEventService auditEventService;
    private final MeterRegistry meterRegistry;

    public AnswerFeedbackService(
        AssistantResponseRecordRepository responseRecordRepository,
        AssistantResponseFeedbackRepository feedbackRepository,
        AuditEventService auditEventService,
        MeterRegistry meterRegistry
    ) {
        this.responseRecordRepository = responseRecordRepository;
        this.feedbackRepository = feedbackRepository;
        this.auditEventService = auditEventService;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public AnswerFeedbackResponse submitFeedback(AnswerFeedbackRequest request) {
        try {
            AssistantResponseRecord responseRecord = responseRecordRepository.findById(request.getResponseId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Response ID not found."));

            String username = CurrentUserProvider.currentUsername();
            Instant now = Instant.now();
            FeedbackVerdict previousVerdict = null;
            boolean created = false;

            AssistantResponseFeedback feedback = feedbackRepository
                .findByResponseRecordIdAndSubmittedBy(responseRecord.getId(), username)
                .orElse(null);
            if (feedback == null) {
                feedback = new AssistantResponseFeedback();
                feedback.setId(UUID.randomUUID());
                feedback.setResponseRecord(responseRecord);
                feedback.setSubmittedBy(username);
                feedback.setCreatedAt(now);
                created = true;
            } else {
                previousVerdict = feedback.getVerdict();
            }

            feedback.setVerdict(request.getVerdict());
            feedback.setComment(request.getComment());
            feedback.setUpdatedAt(now);
            feedbackRepository.save(feedback);

            long goodCount = feedbackRepository.countByResponseRecordIdAndVerdict(responseRecord.getId(), FeedbackVerdict.GOOD);
            long badCount = feedbackRepository.countByResponseRecordIdAndVerdict(responseRecord.getId(), FeedbackVerdict.BAD);
            double goodRate = (goodCount + badCount) == 0L ? 0.0d : (double) goodCount / (double) (goodCount + badCount);

            meterRegistry.counter(
                MetricNames.FEEDBACK_SUBMISSIONS_TOTAL,
                "verdict", feedback.getVerdict() == null ? "unknown" : feedback.getVerdict().name(),
                "action", created ? "create" : "update",
                "changed", Boolean.toString(previousVerdict != feedback.getVerdict())
            ).increment();
            DistributionSummary.builder(MetricNames.FEEDBACK_GOOD_RATE)
                .register(meterRegistry)
                .record(goodRate);

            auditEventService.record(
                "ASSISTANT_FEEDBACK",
                "assistant_response_record",
                responseRecord.getId().toString(),
                200,
                java.util.Map.of(
                    "verdict", feedback.getVerdict() == null ? "unknown" : feedback.getVerdict().name(),
                    "action", created ? "create" : "update"
                )
            );

            return new AnswerFeedbackResponse(
                responseRecord.getId().toString(),
                feedback.getVerdict(),
                goodCount,
                badCount,
                goodRate
            );
        } catch (ResponseStatusException ex) {
            auditEventService.record(
                "ASSISTANT_FEEDBACK",
                "assistant_response_record",
                request.getResponseId() == null ? null : request.getResponseId().toString(),
                ex.getStatusCode().value(),
                java.util.Map.of("error", ex.getReason() == null ? ex.getClass().getSimpleName() : ex.getReason())
            );
            throw ex;
        } catch (RuntimeException ex) {
            auditEventService.record(
                "ASSISTANT_FEEDBACK",
                "assistant_response_record",
                request.getResponseId() == null ? null : request.getResponseId().toString(),
                500,
                java.util.Map.of("error", ex.getClass().getSimpleName())
            );
            throw ex;
        }
    }
}

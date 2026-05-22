package com.example.knowledgecopilot.dto;

import com.example.knowledgecopilot.entity.FeedbackVerdict;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "User feedback for a generated answer.")
public class AnswerFeedbackRequest {
    @NotNull
    @Schema(description = "Ask response ID to attach feedback to.")
    private UUID responseId;

    @NotNull
    @Schema(description = "GOOD or BAD.")
    private FeedbackVerdict verdict;

    @Schema(description = "Optional feedback comment.")
    private String comment;

    public UUID getResponseId() { return responseId; }
    public void setResponseId(UUID responseId) { this.responseId = responseId; }
    public FeedbackVerdict getVerdict() { return verdict; }
    public void setVerdict(FeedbackVerdict verdict) { this.verdict = verdict; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}

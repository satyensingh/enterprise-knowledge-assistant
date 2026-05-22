package com.example.knowledgecopilot.dto;

import com.example.knowledgecopilot.entity.FeedbackVerdict;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Feedback acknowledgement and aggregate counters.")
public class AnswerFeedbackResponse {
    @Schema(description = "Response ID for which feedback was stored.")
    private String responseId;
    @Schema(description = "Current user's latest verdict.")
    private FeedbackVerdict userVerdict;
    @Schema(description = "Total GOOD votes for this clustered question.")
    private Long goodCount;
    @Schema(description = "Total BAD votes for this clustered question.")
    private Long badCount;
    @Schema(description = "GOOD vote ratio in range [0, 1].")
    private Double goodRate;

    public AnswerFeedbackResponse() {}

    public AnswerFeedbackResponse(String responseId, FeedbackVerdict userVerdict, Long goodCount, Long badCount, Double goodRate) {
        this.responseId = responseId;
        this.userVerdict = userVerdict;
        this.goodCount = goodCount;
        this.badCount = badCount;
        this.goodRate = goodRate;
    }

    public String getResponseId() { return responseId; }
    public void setResponseId(String responseId) { this.responseId = responseId; }
    public FeedbackVerdict getUserVerdict() { return userVerdict; }
    public void setUserVerdict(FeedbackVerdict userVerdict) { this.userVerdict = userVerdict; }
    public Long getGoodCount() { return goodCount; }
    public void setGoodCount(Long goodCount) { this.goodCount = goodCount; }
    public Long getBadCount() { return badCount; }
    public void setBadCount(Long badCount) { this.badCount = badCount; }
    public Double getGoodRate() { return goodRate; }
    public void setGoodRate(Double goodRate) { this.goodRate = goodRate; }
}

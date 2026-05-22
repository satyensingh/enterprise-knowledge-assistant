package com.example.knowledgecopilot.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class EvaluationFeedbackRequest {
    @Min(1)
    @Max(5)
    private Integer score;
    private String comment;

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}

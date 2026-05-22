package com.example.knowledgecopilot.dto;

import java.time.Instant;
import java.util.List;

public class GoldenQuestionDto {
    private String id;
    private String question;
    private List<String> expectedSources;
    private Instant createdAt;
    private Instant updatedAt;

    public GoldenQuestionDto() {}

    public GoldenQuestionDto(String id, String question, List<String> expectedSources, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.question = question;
        this.expectedSources = expectedSources;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<String> getExpectedSources() { return expectedSources; }
    public void setExpectedSources(List<String> expectedSources) { this.expectedSources = expectedSources; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

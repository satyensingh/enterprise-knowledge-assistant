package com.example.knowledgecopilot.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class CreateGoldenQuestionRequest {
    @NotBlank
    private String question;
    private List<String> expectedSources;

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<String> getExpectedSources() { return expectedSources; }
    public void setExpectedSources(List<String> expectedSources) { this.expectedSources = expectedSources; }
}

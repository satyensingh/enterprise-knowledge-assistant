package com.example.knowledgecopilot.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evaluation_run")
public class EvaluationRun {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "golden_question_id", nullable = false)
    private GoldenQuestion goldenQuestion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(columnDefinition = "TEXT")
    private String expectedSourcesJson;

    @Column(columnDefinition = "TEXT")
    private String retrievedChunksJson;

    @Column(columnDefinition = "TEXT")
    private String citationsJson;

    @Column(columnDefinition = "TEXT")
    private String generatedAnswer;

    @Column(nullable = false)
    private Double citationCorrectness;

    @Column(nullable = false)
    private Integer expectedSourceCount;

    @Column(nullable = false)
    private Integer matchedExpectedSourceCount;

    private Integer userFeedbackScore;

    @Column(columnDefinition = "TEXT")
    private String userFeedbackComment;

    private Integer questionFeedbackGoodCount;

    private Integer questionFeedbackBadCount;

    private Double questionFeedbackGoodRate;

    @Column(nullable = false)
    private Long latencyMs;

    @Column(nullable = false)
    private String modelUsed;

    @Column(nullable = false)
    private String promptVersion;

    @Column(nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public GoldenQuestion getGoldenQuestion() { return goldenQuestion; }
    public void setGoldenQuestion(GoldenQuestion goldenQuestion) { this.goldenQuestion = goldenQuestion; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getExpectedSourcesJson() { return expectedSourcesJson; }
    public void setExpectedSourcesJson(String expectedSourcesJson) { this.expectedSourcesJson = expectedSourcesJson; }
    public String getRetrievedChunksJson() { return retrievedChunksJson; }
    public void setRetrievedChunksJson(String retrievedChunksJson) { this.retrievedChunksJson = retrievedChunksJson; }
    public String getCitationsJson() { return citationsJson; }
    public void setCitationsJson(String citationsJson) { this.citationsJson = citationsJson; }
    public String getGeneratedAnswer() { return generatedAnswer; }
    public void setGeneratedAnswer(String generatedAnswer) { this.generatedAnswer = generatedAnswer; }
    public Double getCitationCorrectness() { return citationCorrectness; }
    public void setCitationCorrectness(Double citationCorrectness) { this.citationCorrectness = citationCorrectness; }
    public Integer getExpectedSourceCount() { return expectedSourceCount; }
    public void setExpectedSourceCount(Integer expectedSourceCount) { this.expectedSourceCount = expectedSourceCount; }
    public Integer getMatchedExpectedSourceCount() { return matchedExpectedSourceCount; }
    public void setMatchedExpectedSourceCount(Integer matchedExpectedSourceCount) { this.matchedExpectedSourceCount = matchedExpectedSourceCount; }
    public Integer getUserFeedbackScore() { return userFeedbackScore; }
    public void setUserFeedbackScore(Integer userFeedbackScore) { this.userFeedbackScore = userFeedbackScore; }
    public String getUserFeedbackComment() { return userFeedbackComment; }
    public void setUserFeedbackComment(String userFeedbackComment) { this.userFeedbackComment = userFeedbackComment; }
    public Integer getQuestionFeedbackGoodCount() { return questionFeedbackGoodCount; }
    public void setQuestionFeedbackGoodCount(Integer questionFeedbackGoodCount) { this.questionFeedbackGoodCount = questionFeedbackGoodCount; }
    public Integer getQuestionFeedbackBadCount() { return questionFeedbackBadCount; }
    public void setQuestionFeedbackBadCount(Integer questionFeedbackBadCount) { this.questionFeedbackBadCount = questionFeedbackBadCount; }
    public Double getQuestionFeedbackGoodRate() { return questionFeedbackGoodRate; }
    public void setQuestionFeedbackGoodRate(Double questionFeedbackGoodRate) { this.questionFeedbackGoodRate = questionFeedbackGoodRate; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

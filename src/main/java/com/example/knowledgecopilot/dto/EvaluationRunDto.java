package com.example.knowledgecopilot.dto;

import com.example.knowledgecopilot.retrieval.api.RetrievedChunkTrace;

import java.time.Instant;
import java.util.List;

public class EvaluationRunDto {
    private String id;
    private String goldenQuestionId;
    private String goldenQuestion;
    private List<String> expectedSources;
    private List<RetrievedChunkTrace> retrievedChunks;
    private List<CitationDto> citations;
    private String generatedAnswer;
    private Double citationCorrectness;
    private Integer expectedSourceCount;
    private Integer matchedExpectedSourceCount;
    private Integer userFeedbackScore;
    private String userFeedbackComment;
    private Integer questionFeedbackGoodCount;
    private Integer questionFeedbackBadCount;
    private Double questionFeedbackGoodRate;
    private Long latencyMs;
    private String modelUsed;
    private String promptVersion;
    private Instant createdAt;

    public EvaluationRunDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getGoldenQuestionId() { return goldenQuestionId; }
    public void setGoldenQuestionId(String goldenQuestionId) { this.goldenQuestionId = goldenQuestionId; }
    public String getGoldenQuestion() { return goldenQuestion; }
    public void setGoldenQuestion(String goldenQuestion) { this.goldenQuestion = goldenQuestion; }
    public List<String> getExpectedSources() { return expectedSources; }
    public void setExpectedSources(List<String> expectedSources) { this.expectedSources = expectedSources; }
    public List<RetrievedChunkTrace> getRetrievedChunks() { return retrievedChunks; }
    public void setRetrievedChunks(List<RetrievedChunkTrace> retrievedChunks) { this.retrievedChunks = retrievedChunks; }
    public List<CitationDto> getCitations() { return citations; }
    public void setCitations(List<CitationDto> citations) { this.citations = citations; }
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

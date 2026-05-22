package com.example.knowledgecopilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assistant_response_record")
public class AssistantResponseRecord {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String askedBy;

    @ManyToOne(optional = false)
    @JoinColumn(name = "question_cluster_id", nullable = false)
    private QuestionCluster questionCluster;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String generatedAnswer;

    @Column(columnDefinition = "TEXT")
    private String citationsJson;

    @Column(columnDefinition = "TEXT")
    private String retrievedChunksJson;

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
    public String getAskedBy() { return askedBy; }
    public void setAskedBy(String askedBy) { this.askedBy = askedBy; }
    public QuestionCluster getQuestionCluster() { return questionCluster; }
    public void setQuestionCluster(QuestionCluster questionCluster) { this.questionCluster = questionCluster; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getGeneratedAnswer() { return generatedAnswer; }
    public void setGeneratedAnswer(String generatedAnswer) { this.generatedAnswer = generatedAnswer; }
    public String getCitationsJson() { return citationsJson; }
    public void setCitationsJson(String citationsJson) { this.citationsJson = citationsJson; }
    public String getRetrievedChunksJson() { return retrievedChunksJson; }
    public void setRetrievedChunksJson(String retrievedChunksJson) { this.retrievedChunksJson = retrievedChunksJson; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public String getModelUsed() { return modelUsed; }
    public void setModelUsed(String modelUsed) { this.modelUsed = modelUsed; }
    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

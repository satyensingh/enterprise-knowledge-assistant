package com.example.knowledgecopilot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import com.example.knowledgecopilot.util.EmbeddingJsonCodec;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "question_cluster")
public class QuestionCluster {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String normalizedQuestion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String representativeQuestion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String embeddingJson;

    @Transient
    private float[] embedding;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getNormalizedQuestion() { return normalizedQuestion; }
    public void setNormalizedQuestion(String normalizedQuestion) { this.normalizedQuestion = normalizedQuestion; }
    public String getRepresentativeQuestion() { return representativeQuestion; }
    public void setRepresentativeQuestion(String representativeQuestion) { this.representativeQuestion = representativeQuestion; }
    public float[] getEmbedding() {
        if (embedding == null) {
            embedding = EmbeddingJsonCodec.fromJson(embeddingJson);
        }
        return embedding;
    }
    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
        this.embeddingJson = EmbeddingJsonCodec.toJson(embedding);
    }
    public String getEmbeddingJson() { return embeddingJson; }
    public void setEmbeddingJson(String embeddingJson) {
        this.embeddingJson = embeddingJson;
        this.embedding = null;
    }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

package com.example.knowledgecopilot.entity;

import jakarta.persistence.*;
import com.example.knowledgecopilot.util.EmbeddingJsonCodec;

import java.util.UUID;

@Entity
@Table(name = "knowledge_chunk")
public class KnowledgeChunk {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private KnowledgeDocument document;

    @Column(nullable = false)
    private Integer chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String embeddingJson;

    @Transient
    private float[] embedding;

    @Column(nullable = false)
    private String citationLabel;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public KnowledgeDocument getDocument() { return document; }
    public void setDocument(KnowledgeDocument document) { this.document = document; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
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
    public String getCitationLabel() { return citationLabel; }
    public void setCitationLabel(String citationLabel) { this.citationLabel = citationLabel; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}

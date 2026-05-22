package com.example.knowledgecopilot.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "assistant_response_feedback",
    uniqueConstraints = @UniqueConstraint(
        name = "assistant_response_feedback_response_id_user_key",
        columnNames = {"response_id", "submitted_by"}
    )
)
public class AssistantResponseFeedback {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "response_id", nullable = false)
    private AssistantResponseRecord responseRecord;

    @Column(name = "submitted_by", nullable = false)
    private String submittedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeedbackVerdict verdict;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public AssistantResponseRecord getResponseRecord() { return responseRecord; }
    public void setResponseRecord(AssistantResponseRecord responseRecord) { this.responseRecord = responseRecord; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public FeedbackVerdict getVerdict() { return verdict; }
    public void setVerdict(FeedbackVerdict verdict) { this.verdict = verdict; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

package com.impactiq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Developer review of an investigation. Only this can mark a hypothesis CONFIRMED. */
@Entity
@Table(name = "investigation_feedback", indexes = @Index(name = "idx_investigation_feedback_run", columnList = "investigation_id"))
public class InvestigationFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "investigation_id", nullable = false)
    private Long investigationId;

    /** Null when the feedback is about the whole report. */
    private Integer hypothesisRank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FeedbackVerdict verdict;

    @Column(length = 2000)
    private String comment;

    private Long userId;

    @Column(length = 64)
    private String username;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Long getInvestigationId() {
        return investigationId;
    }

    public void setInvestigationId(Long investigationId) {
        this.investigationId = investigationId;
    }

    public Integer getHypothesisRank() {
        return hypothesisRank;
    }

    public void setHypothesisRank(Integer hypothesisRank) {
        this.hypothesisRank = hypothesisRank;
    }

    public FeedbackVerdict getVerdict() {
        return verdict;
    }

    public void setVerdict(FeedbackVerdict verdict) {
        this.verdict = verdict;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

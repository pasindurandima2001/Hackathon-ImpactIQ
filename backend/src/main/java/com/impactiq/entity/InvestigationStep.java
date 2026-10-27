package com.impactiq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/** Persisted stage of an investigation, so failures can be diagnosed and successful stages reused on retry. */
@Entity
@Table(name = "investigation_steps", indexes = @Index(name = "idx_investigation_steps_run", columnList = "investigation_id"))
public class InvestigationStep {

    /** Maps to MariaDB MEDIUMTEXT. */
    private static final int OUTPUT_COLUMN_LENGTH = 16_777_215;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "investigation_id", nullable = false)
    private Long investigationId;

    @Column(nullable = false, length = 48)
    private String agentName;

    @Column(nullable = false)
    private int sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StepStatus status = StepStatus.PENDING;

    private int attempts;

    private Long durationMs;

    @Column(length = 48)
    private String errorCategory;

    @Column(length = 500)
    private String errorMessage;

    @Column(length = 500)
    private String summary;

    @Lob
    @Column(length = OUTPUT_COLUMN_LENGTH)
    private String outputJson;

    private Instant startedAt;

    private Instant completedAt;

    public Long getId() {
        return id;
    }

    public Long getInvestigationId() {
        return investigationId;
    }

    public void setInvestigationId(Long investigationId) {
        this.investigationId = investigationId;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public StepStatus getStatus() {
        return status;
    }

    public void setStatus(StepStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getErrorCategory() {
        return errorCategory;
    }

    public void setErrorCategory(String errorCategory) {
        this.errorCategory = errorCategory;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getOutputJson() {
        return outputJson;
    }

    public void setOutputJson(String outputJson) {
        this.outputJson = outputJson;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}

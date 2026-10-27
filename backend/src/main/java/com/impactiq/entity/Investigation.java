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
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One multi-agent investigation run for a defect. The deterministic baseline score is snapshotted
 * at creation; agents never write it.
 */
@Entity
@Table(name = "investigations", indexes = {
        @Index(name = "idx_investigations_defect", columnList = "defect_id, created_at"),
        @Index(name = "idx_investigations_status", columnList = "status")
})
public class Investigation {

    /** Maps to MariaDB LONGTEXT. */
    private static final int REPORT_COLUMN_LENGTH = 100_000_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "defect_id", nullable = false)
    private Long defectId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "component_id")
    private Long componentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvestigationStatus status = InvestigationStatus.QUEUED;

    /** 1 for a fresh run, incremented for each retry in the same chain. */
    @Column(nullable = false)
    private int attempt = 1;

    private Long parentInvestigationId;

    private Long triggeredBy;

    @Column(length = 16)
    private String engine;

    @Column(length = 64)
    private String model;

    private int baselineScore;

    @Column(length = 16)
    private String baselineRiskLevel;

    @Column(length = 4)
    private String baselinePriority;

    @Lob
    @Column(length = REPORT_COLUMN_LENGTH)
    private String reportJson;

    @Column(length = 48)
    private String errorCategory;

    @Column(length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Instant startedAt;

    private Instant completedAt;

    @Version
    private long version;

    public Long getId() {
        return id;
    }

    public Long getDefectId() {
        return defectId;
    }

    public void setDefectId(Long defectId) {
        this.defectId = defectId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getComponentId() {
        return componentId;
    }

    public void setComponentId(Long componentId) {
        this.componentId = componentId;
    }

    public InvestigationStatus getStatus() {
        return status;
    }

    public void setStatus(InvestigationStatus status) {
        this.status = status;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public Long getParentInvestigationId() {
        return parentInvestigationId;
    }

    public void setParentInvestigationId(Long parentInvestigationId) {
        this.parentInvestigationId = parentInvestigationId;
    }

    public Long getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(Long triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getBaselineScore() {
        return baselineScore;
    }

    public void setBaselineScore(int baselineScore) {
        this.baselineScore = baselineScore;
    }

    public String getBaselineRiskLevel() {
        return baselineRiskLevel;
    }

    public void setBaselineRiskLevel(String baselineRiskLevel) {
        this.baselineRiskLevel = baselineRiskLevel;
    }

    public String getBaselinePriority() {
        return baselinePriority;
    }

    public void setBaselinePriority(String baselinePriority) {
        this.baselinePriority = baselinePriority;
    }

    public String getReportJson() {
        return reportJson;
    }

    public void setReportJson(String reportJson) {
        this.reportJson = reportJson;
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

    public Instant getCreatedAt() {
        return createdAt;
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

    public long getVersion() {
        return version;
    }
}

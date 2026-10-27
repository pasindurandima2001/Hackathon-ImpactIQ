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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A logical knowledge source within one project. Each upload creates a {@link DocumentVersion};
 * only the active version's chunks are retrievable.
 */
@Entity
@Table(name = "knowledge_documents", indexes = @Index(name = "idx_knowledge_documents_project", columnList = "project_id"))
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "component_id")
    private Long componentId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 16)
    private String sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentStatus status = DocumentStatus.PENDING;

    /** Version whose chunks are currently indexed; null until the first successful indexing. */
    private Integer activeVersionNumber;

    @Column(nullable = false)
    private int latestVersionNumber;

    @Column(length = 1000)
    private String lastError;

    private Long uploadedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public Integer getActiveVersionNumber() {
        return activeVersionNumber;
    }

    public void setActiveVersionNumber(Integer activeVersionNumber) {
        this.activeVersionNumber = activeVersionNumber;
    }

    public int getLatestVersionNumber() {
        return latestVersionNumber;
    }

    public void setLatestVersionNumber(int latestVersionNumber) {
        this.latestVersionNumber = latestVersionNumber;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Long getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(Long uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

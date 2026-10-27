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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Evidence attached to one defect. Content is stored after secret redaction; the raw upload is not kept. */
@Entity
@Table(name = "defect_evidence", indexes = @Index(name = "idx_defect_evidence_defect", columnList = "defect_id"))
public class DefectEvidence {

    /** Maps to MariaDB MEDIUMTEXT. */
    private static final int CONTENT_COLUMN_LENGTH = 16_777_215;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "defect_id", nullable = false)
    private Long defectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private EvidenceType type;

    @Column(nullable = false, length = 255)
    private String label;

    @Lob
    @Column(nullable = false, length = CONTENT_COLUMN_LENGTH)
    private String content;

    @Column(length = 255)
    private String sourceFilename;

    @Column(nullable = false, length = 64)
    private String contentSha256;

    private int redactionCount;

    private boolean suspicious;

    private Long createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Long getDefectId() {
        return defectId;
    }

    public void setDefectId(Long defectId) {
        this.defectId = defectId;
    }

    public EvidenceType getType() {
        return type;
    }

    public void setType(EvidenceType type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public void setContentSha256(String contentSha256) {
        this.contentSha256 = contentSha256;
    }

    public int getRedactionCount() {
        return redactionCount;
    }

    public void setRedactionCount(int redactionCount) {
        this.redactionCount = redactionCount;
    }

    public boolean isSuspicious() {
        return suspicious;
    }

    public void setSuspicious(boolean suspicious) {
        this.suspicious = suspicious;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

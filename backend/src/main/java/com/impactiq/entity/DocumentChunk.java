package com.impactiq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * One retrievable slice of a document version. Text is stored after secret redaction.
 * The embedding is a little-endian float32 vector, L2-normalised so cosine similarity is a dot product.
 */
@Entity
@Table(name = "document_chunks", indexes = {
        @Index(name = "idx_document_chunks_project", columnList = "project_id, component_id"),
        @Index(name = "idx_document_chunks_version", columnList = "version_id")
})
public class DocumentChunk {

    /** Maps to MariaDB MEDIUMTEXT / MEDIUMBLOB. */
    private static final int MEDIUM_COLUMN_LENGTH = 16_777_215;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "component_id")
    private Long componentId;

    @Column(nullable = false)
    private int chunkIndex;

    @Lob
    @Column(nullable = false, length = MEDIUM_COLUMN_LENGTH)
    private String content;

    private Integer startLine;

    private Integer endLine;

    private Integer pageNumber;

    @Lob
    @Column(length = MEDIUM_COLUMN_LENGTH)
    private byte[] embedding;

    @Column(length = 64)
    private String embeddingModel;

    /** True when the text contains instruction-like phrases; it is still treated as data only. */
    private boolean suspicious;

    public Long getId() {
        return id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
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

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getStartLine() {
        return startLine;
    }

    public void setStartLine(Integer startLine) {
        this.startLine = startLine;
    }

    public Integer getEndLine() {
        return endLine;
    }

    public void setEndLine(Integer endLine) {
        this.endLine = endLine;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public byte[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(byte[] embedding) {
        this.embedding = embedding;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public boolean isSuspicious() {
        return suspicious;
    }

    public void setSuspicious(boolean suspicious) {
        this.suspicious = suspicious;
    }
}

package com.impactiq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One chat turn. Role, status and answer status are plain VARCHAR columns (see V3__chatbot.sql) so
 * ddl-auto=update never creates native ENUM columns. User content is stored only after secret redaction.
 */
@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_messages_conversation", columnList = "conversation_id, id"),
        @Index(name = "idx_chat_messages_rate", columnList = "owner_user_id, role, created_at"),
        @Index(name = "idx_chat_messages_status", columnList = "status, created_at")
})
public class ChatMessage {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";
    public static final String STATUS_COMPLETE = "COMPLETE";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ERROR = "ERROR";

    /** Maps to MariaDB MEDIUMTEXT. */
    private static final int MEDIUM_COLUMN_LENGTH = 16_777_215;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 16)
    private String role;

    @Column(nullable = false, length = 16)
    private String status;

    @Lob
    @Column(nullable = false, length = MEDIUM_COLUMN_LENGTH)
    private String content;

    @Column(name = "answer_status", length = 32)
    private String answerStatus;

    @Lob
    @Column(name = "citations_json", length = MEDIUM_COLUMN_LENGTH)
    private String citationsJson;

    @Lob
    @Column(name = "details_json", length = MEDIUM_COLUMN_LENGTH)
    private String detailsJson;

    @Column(name = "redaction_count", nullable = false)
    private int redactionCount;

    @Column(nullable = false)
    private boolean suspicious;

    @Column(length = 16)
    private String engine;

    @Column(length = 64)
    private String model;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_category", length = 48)
    private String errorCategory;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public Long getId() {
        return id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAnswerStatus() {
        return answerStatus;
    }

    public void setAnswerStatus(String answerStatus) {
        this.answerStatus = answerStatus;
    }

    public String getCitationsJson() {
        return citationsJson;
    }

    public void setCitationsJson(String citationsJson) {
        this.citationsJson = citationsJson;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}

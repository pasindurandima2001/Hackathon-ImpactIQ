package com.impactiq.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A private chatbot conversation scoped to exactly one project. Only the owner can read or change it;
 * project membership is re-checked on every request. Schema: backend/db/migrations/V3__chatbot.sql.
 */
@Entity
@Table(name = "chat_conversations", indexes = {
        @Index(name = "idx_chat_conversations_owner", columnList = "owner_user_id, deleted_at, updated_at"),
        @Index(name = "idx_chat_conversations_project", columnList = "project_id"),
        @Index(name = "idx_chat_conversations_updated", columnList = "updated_at")
})
public class ChatConversation {

    public static final int TITLE_MAX = 120;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "component_id")
    private Long componentId;

    @Column(name = "defect_id")
    private Long defectId;

    @Column(nullable = false, length = TITLE_MAX)
    private String title;

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private long version;

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
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

    public Long getDefectId() {
        return defectId;
    }

    public void setDefectId(Long defectId) {
        this.defectId = defectId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public long getVersion() {
        return version;
    }
}

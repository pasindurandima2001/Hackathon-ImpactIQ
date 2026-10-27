package com.impactiq.service;

import com.impactiq.config.ChatProperties;
import com.impactiq.config.RetentionProperties;
import com.impactiq.entity.DocumentVersion;
import com.impactiq.entity.KnowledgeDocument;
import com.impactiq.rag.DocumentStorageService;
import com.impactiq.repository.AuditEventRepository;
import com.impactiq.repository.ChatConversationRepository;
import com.impactiq.repository.ChatMessageRepository;
import com.impactiq.repository.DocumentVersionRepository;
import com.impactiq.repository.KnowledgeDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Daily retention: prunes old audit events, deletes stored originals of superseded document versions, and purges
 * expired chatbot conversations. Version metadata is kept for history; the active version's file is never deleted.
 * Chat retention only touches chat tables.
 */
@Service
public class RetentionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetentionService.class);

    private final RetentionProperties properties;
    private final ChatProperties chatProperties;
    private final AuditEventRepository auditEventRepository;
    private final DocumentVersionRepository versionRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentStorageService storageService;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;

    public RetentionService(
            RetentionProperties properties,
            ChatProperties chatProperties,
            AuditEventRepository auditEventRepository,
            DocumentVersionRepository versionRepository,
            KnowledgeDocumentRepository documentRepository,
            DocumentStorageService storageService,
            ChatConversationRepository conversationRepository,
            ChatMessageRepository messageRepository
    ) {
        this.properties = properties;
        this.chatProperties = chatProperties;
        this.auditEventRepository = auditEventRepository;
        this.versionRepository = versionRepository;
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Scheduled(cron = "${impactiq.retention.cron:0 30 3 * * *}")
    @Transactional
    public void applyRetention() {
        Instant now = Instant.now();
        int audits = auditEventRepository.deleteOlderThan(now.minus(Duration.ofDays(properties.getAuditDays())));
        int files = 0;
        for (DocumentVersion version : versionRepository.findByFileDeletedFalseAndCreatedAtBefore(
                now.minus(Duration.ofDays(properties.getSupersededFileDays())))) {
            KnowledgeDocument document = documentRepository.findById(version.getDocumentId()).orElse(null);
            boolean superseded = document != null && document.getLatestVersionNumber() > version.getVersionNumber()
                    && !Objects.equals(document.getActiveVersionNumber(), version.getVersionNumber());
            if (superseded) {
                storageService.delete(version.getProjectId(), version.getStoredFilename());
                version.setFileDeleted(true);
                files++;
            }
        }
        int conversations = purgeChats(now);
        if (audits > 0 || files > 0 || conversations > 0) {
            LOGGER.info("Retention removed {} audit event(s), {} superseded file(s) and {} chat conversation(s)",
                    audits, files, conversations);
        }
    }

    /**
     * Removes conversations the user deleted (after a short grace that keeps rate-limit rows countable) and, when
     * impactiq.chat.retention-days is positive, conversations inactive for longer than that. A value of 0 or less
     * disables only the age-based purge; it never widens what is deleted.
     */
    int purgeChats(Instant now) {
        Set<Long> expired = new LinkedHashSet<>(conversationRepository.findDeletedBefore(
                now.minus(Duration.ofHours(Math.max(1, chatProperties.getDeletedPurgeHours())))));
        if (chatProperties.getRetentionDays() > 0) {
            expired.addAll(conversationRepository.findInactiveBefore(now.minus(Duration.ofDays(chatProperties.getRetentionDays()))));
        }
        if (expired.isEmpty()) {
            return 0;
        }
        List<Long> ids = List.copyOf(expired);
        messageRepository.deleteByConversationIdIn(ids);
        return conversationRepository.deleteByIdIn(ids);
    }
}

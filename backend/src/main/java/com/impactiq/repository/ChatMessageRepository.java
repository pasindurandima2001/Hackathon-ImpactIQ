package com.impactiq.repository;

import com.impactiq.entity.ChatMessage;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByIdAsc(Long conversationId);

    /** Most recent messages first; callers reverse them for chronological prompts. */
    List<ChatMessage> findByConversationIdOrderByIdDesc(Long conversationId, Limit limit);

    Optional<ChatMessage> findByIdAndConversationId(Long id, Long conversationId);

    /** Model invocations (assistant rows) by one user since a point in time: the rate-limit counter. */
    long countByOwnerUserIdAndRoleAndCreatedAtAfter(Long ownerUserId, String role, Instant after);

    Optional<ChatMessage> findFirstByOwnerUserIdAndRoleAndCreatedAtAfterOrderByCreatedAtAsc(Long ownerUserId, String role, Instant after);

    boolean existsByOwnerUserIdAndRoleAndStatusAndCreatedAtAfter(Long ownerUserId, String role, String status, Instant after);

    List<ChatMessage> findByStatusAndCreatedAtBefore(String status, Instant before);

    /** Wipes content when a conversation is deleted; rows stay until retention so rate limits still count them. */
    @Modifying
    @Query("""
            update ChatMessage m set m.content = '', m.citationsJson = null, m.detailsJson = null
            where m.conversationId = :conversationId
            """)
    int wipeConversation(@Param("conversationId") Long conversationId);

    @Modifying
    @Query("delete from ChatMessage m where m.conversationId in :conversationIds")
    int deleteByConversationIdIn(@Param("conversationIds") Collection<Long> conversationIds);
}

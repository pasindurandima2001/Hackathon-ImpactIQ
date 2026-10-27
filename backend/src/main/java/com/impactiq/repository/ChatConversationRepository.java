package com.impactiq.repository;

import com.impactiq.entity.ChatConversation;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    /** Ownership is part of the lookup, so another user's conversation is indistinguishable from a missing one. */
    Optional<ChatConversation> findByIdAndOwnerUserIdAndDeletedAtIsNull(Long id, Long ownerUserId);

    @Query("""
            select c from ChatConversation c
            where c.ownerUserId = :ownerUserId and c.deletedAt is null and c.projectId in :projectIds
            order by c.updatedAt desc, c.id desc
            """)
    List<ChatConversation> findVisible(
            @Param("ownerUserId") Long ownerUserId,
            @Param("projectIds") Collection<Long> projectIds,
            Limit limit
    );

    @Query("select c.id from ChatConversation c where c.deletedAt is not null and c.deletedAt < :cutoff")
    List<Long> findDeletedBefore(@Param("cutoff") Instant cutoff);

    @Query("select c.id from ChatConversation c where c.projectId = :projectId")
    List<Long> findIdsByProjectId(@Param("projectId") Long projectId);

    @Query("select c.id from ChatConversation c where c.updatedAt < :cutoff")
    List<Long> findInactiveBefore(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("delete from ChatConversation c where c.id in :ids")
    int deleteByIdIn(@Param("ids") Collection<Long> ids);
}

package com.impactiq.repository;

import com.impactiq.entity.DocumentStatus;
import com.impactiq.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findByProjectIdOrderByUpdatedAtDesc(Long projectId);

    Optional<KnowledgeDocument> findByIdAndProjectId(Long id, Long projectId);

    List<KnowledgeDocument> findByStatusIn(Collection<DocumentStatus> statuses);

    long countByProjectIdAndStatus(Long projectId, DocumentStatus status);

    @Modifying
    @Query("delete from KnowledgeDocument d where d.projectId = :projectId")
    int deleteByProjectId(@Param("projectId") Long projectId);
}

package com.impactiq.repository;

import com.impactiq.entity.DocumentChunk;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    /**
     * Retrieval candidates are always scoped to exactly one project. When a component is given,
     * only that component's chunks and project-wide chunks (no component) are eligible.
     */
    @Query("""
            select c from DocumentChunk c
            where c.projectId = :projectId
              and (:componentId is null or c.componentId is null or c.componentId = :componentId)
            order by c.id
            """)
    List<DocumentChunk> findRetrievalCandidates(
            @Param("projectId") Long projectId,
            @Param("componentId") Long componentId,
            Limit limit
    );

    List<DocumentChunk> findByVersionIdOrderByChunkIndexAsc(Long versionId);

    long countByProjectId(Long projectId);

    @Modifying
    @Query("delete from DocumentChunk c where c.documentId = :documentId and c.versionId <> :keepVersionId")
    int deleteOtherVersions(@Param("documentId") Long documentId, @Param("keepVersionId") Long keepVersionId);

    @Modifying
    @Query("delete from DocumentChunk c where c.versionId = :versionId")
    int deleteByVersionId(@Param("versionId") Long versionId);

    @Modifying
    @Query("delete from DocumentChunk c where c.documentId = :documentId")
    int deleteByDocumentId(@Param("documentId") Long documentId);

    @Modifying
    @Query("delete from DocumentChunk c where c.projectId = :projectId")
    int deleteByProjectId(@Param("projectId") Long projectId);
}

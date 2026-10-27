package com.impactiq.repository;

import com.impactiq.entity.DocumentStatus;
import com.impactiq.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(Long documentId);

    Optional<DocumentVersion> findByDocumentIdAndVersionNumber(Long documentId, int versionNumber);

    List<DocumentVersion> findByStatusIn(Collection<DocumentStatus> statuses);

    List<DocumentVersion> findByFileDeletedFalseAndCreatedAtBefore(Instant cutoff);

    void deleteByDocumentId(Long documentId);

    List<DocumentVersion> findByProjectId(Long projectId);

    @Modifying
    @Query("delete from DocumentVersion v where v.projectId = :projectId")
    int deleteByProjectId(@Param("projectId") Long projectId);
}

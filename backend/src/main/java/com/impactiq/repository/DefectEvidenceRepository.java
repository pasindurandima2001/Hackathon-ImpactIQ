package com.impactiq.repository;

import com.impactiq.entity.DefectEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface DefectEvidenceRepository extends JpaRepository<DefectEvidence, Long> {

    List<DefectEvidence> findByDefectIdOrderByCreatedAtAscIdAsc(Long defectId);

    Optional<DefectEvidence> findByIdAndDefectId(Long id, Long defectId);

    long countByDefectId(Long defectId);

    @Query("select distinct e.defectId from DefectEvidence e")
    Set<Long> findDefectIdsWithEvidence();

    @Modifying
    @Query("delete from DefectEvidence e where e.defectId = :defectId")
    int deleteByDefectId(@Param("defectId") Long defectId);
}

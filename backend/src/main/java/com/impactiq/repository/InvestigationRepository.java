package com.impactiq.repository;

import com.impactiq.entity.Investigation;
import com.impactiq.entity.InvestigationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvestigationRepository extends JpaRepository<Investigation, Long> {

    List<Investigation> findByDefectIdOrderByCreatedAtDescIdDesc(Long defectId);

    Optional<Investigation> findByIdAndDefectId(Long id, Long defectId);

    boolean existsByDefectIdAndStatusIn(Long defectId, Collection<InvestigationStatus> statuses);

    long countByStatusIn(Collection<InvestigationStatus> statuses);

    List<Investigation> findByStatusIn(Collection<InvestigationStatus> statuses);

    @Query("select i.id from Investigation i where i.defectId = :defectId")
    List<Long> findIdsByDefectId(@Param("defectId") Long defectId);

    @Modifying
    @Query("delete from Investigation i where i.defectId = :defectId")
    int deleteByDefectId(@Param("defectId") Long defectId);
}

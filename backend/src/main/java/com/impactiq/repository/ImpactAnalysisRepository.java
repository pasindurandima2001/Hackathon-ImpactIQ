package com.impactiq.repository;

import com.impactiq.entity.ImpactAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImpactAnalysisRepository extends JpaRepository<ImpactAnalysis, Long> {

    List<ImpactAnalysis> findByDefectIdOrderByCreatedAtDescIdDesc(Long defectId);

    /**
     * Derived delete: loads each analysis and removes it through the entity manager,
     * so its element-collection rows (recommended actions, affected areas) are removed too.
     * Must run inside the caller's transaction.
     */
    void deleteByDefectId(Long defectId);
}

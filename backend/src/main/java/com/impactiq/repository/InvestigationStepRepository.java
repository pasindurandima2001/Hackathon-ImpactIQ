package com.impactiq.repository;

import com.impactiq.entity.InvestigationStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface InvestigationStepRepository extends JpaRepository<InvestigationStep, Long> {

    List<InvestigationStep> findByInvestigationIdOrderBySequenceNumberAsc(Long investigationId);

    @Modifying
    @Query("delete from InvestigationStep s where s.investigationId in :ids")
    int deleteByInvestigationIdIn(@Param("ids") Collection<Long> ids);
}

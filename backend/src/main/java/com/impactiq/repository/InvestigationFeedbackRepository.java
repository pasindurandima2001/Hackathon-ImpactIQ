package com.impactiq.repository;

import com.impactiq.entity.InvestigationFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface InvestigationFeedbackRepository extends JpaRepository<InvestigationFeedback, Long> {

    List<InvestigationFeedback> findByInvestigationIdOrderByCreatedAtDescIdDesc(Long investigationId);

    @Query("select distinct f.investigationId from InvestigationFeedback f")
    Set<Long> findReviewedInvestigationIds();

    @Modifying
    @Query("delete from InvestigationFeedback f where f.investigationId in :ids")
    int deleteByInvestigationIdIn(@Param("ids") Collection<Long> ids);
}

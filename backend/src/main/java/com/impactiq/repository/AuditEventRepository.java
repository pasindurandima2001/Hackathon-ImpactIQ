package com.impactiq.repository;

import com.impactiq.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findTop100ByProjectIdOrderByCreatedAtDescIdDesc(Long projectId);

    @Modifying
    @Query("delete from AuditEvent a where a.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}

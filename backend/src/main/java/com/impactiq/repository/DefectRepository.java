package com.impactiq.repository;

import com.impactiq.entity.Defect;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DefectRepository extends JpaRepository<Defect, Long> {

    /** Defects reported by one user. */
    List<Defect> findByCreatedBy(Long createdBy);

    long countByProjectId(Long projectId);

    /** Row lock used to serialise "one active investigation per defect" checks. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Defect d where d.id = :id")
    Optional<Defect> findByIdForUpdate(@Param("id") Long id);
}

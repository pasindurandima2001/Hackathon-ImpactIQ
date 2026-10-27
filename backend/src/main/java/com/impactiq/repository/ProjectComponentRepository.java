package com.impactiq.repository;

import com.impactiq.entity.ProjectComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectComponentRepository extends JpaRepository<ProjectComponent, Long> {

    List<ProjectComponent> findByProjectIdOrderByNameAsc(Long projectId);

    List<ProjectComponent> findByIdIn(Collection<Long> ids);

    Optional<ProjectComponent> findByIdAndProjectId(Long id, Long projectId);

    boolean existsByProjectIdAndNameIgnoreCase(Long projectId, String name);

    @Modifying
    @Query("delete from ProjectComponent c where c.projectId = :projectId")
    int deleteByProjectId(@Param("projectId") Long projectId);
}

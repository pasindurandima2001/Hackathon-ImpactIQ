package com.impactiq.repository;

import com.impactiq.entity.ProjectMember;
import com.impactiq.entity.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

    List<ProjectMember> findByUserId(Long userId);

    List<ProjectMember> findByProjectIdOrderByCreatedAtAsc(Long projectId);

    long countByProjectIdAndRole(Long projectId, ProjectRole role);

    @Modifying
    @Query("delete from ProjectMember m where m.projectId = :projectId")
    int deleteByProjectId(@Param("projectId") Long projectId);
}

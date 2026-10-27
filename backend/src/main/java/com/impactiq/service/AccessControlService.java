package com.impactiq.service;

import com.impactiq.entity.Defect;
import com.impactiq.entity.ProjectMember;
import com.impactiq.entity.ProjectRole;
import com.impactiq.exception.ApiException;
import com.impactiq.repository.ProjectMemberRepository;
import com.impactiq.repository.ProjectRepository;
import com.impactiq.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Project-level authorization. Every signed-in user can read every project; unknown projects give 404.
 * Defects: administrators have full access to every defect; other users have full access to the defects they
 * reported (edit, evidence, investigations, chat) and cannot see anyone else's.
 */
@Service
public class AccessControlService {

    private final ProjectMemberRepository memberRepository;
    private final ProjectRepository projectRepository;

    public AccessControlService(ProjectMemberRepository memberRepository, ProjectRepository projectRepository) {
        this.memberRepository = memberRepository;
        this.projectRepository = projectRepository;
    }

    /**
     * Effective role, or empty when the project does not exist. Admins act as OWNER on every project; every other
     * signed-in user can read and chat about every project (VIEWER) unless an administrator granted a higher role.
     */
    public Optional<ProjectRole> roleFor(CurrentUser user, Long projectId) {
        if (user == null || projectId == null) {
            return Optional.empty();
        }
        if (user.admin()) {
            return projectRepository.existsById(projectId) ? Optional.of(ProjectRole.OWNER) : Optional.empty();
        }
        Optional<ProjectRole> membership = memberRepository.findByProjectIdAndUserId(projectId, user.id()).map(ProjectMember::getRole);
        if (membership.isPresent()) {
            return membership;
        }
        return projectRepository.existsById(projectId) ? Optional.of(ProjectRole.VIEWER) : Optional.empty();
    }

    /** Projects, members, components and project knowledge are managed by administrators only. */
    public void requireAdmin(CurrentUser user) {
        if (user == null) {
            throw ApiException.unauthorized("Sign in to continue");
        }
        if (!user.admin()) {
            throw ApiException.forbidden("Only administrators can manage projects and project knowledge");
        }
    }

    public ProjectRole requireProjectRole(CurrentUser user, Long projectId, ProjectRole required) {
        ProjectRole role = roleFor(user, projectId)
                .orElseThrow(() -> ApiException.notFound("Project " + projectId + " was not found"));
        if (!role.atLeast(required)) {
            throw ApiException.forbidden("This action requires the " + required.name() + " project role");
        }
        return role;
    }

    /** Every project is visible to every signed-in user (at least as VIEWER). */
    public Set<Long> visibleProjectIds(CurrentUser user) {
        return projectRepository.findAll().stream().map(project -> project.getId()).collect(Collectors.toSet());
    }

    /** The required role is kept for callers; administrators and the defect's reporter always qualify. */
    public boolean canAccessDefect(CurrentUser user, Defect defect, ProjectRole required) {
        if (user == null) {
            return false;
        }
        if (user.admin()) {
            return true;
        }
        return isReporter(user, defect);
    }

    public void requireDefectAccess(CurrentUser user, Defect defect, ProjectRole required) {
        if (user == null) {
            throw ApiException.unauthorized("Sign in to continue");
        }
        if (user.admin()) {
            return;
        }
        // Other users' defects are reported as missing, so their existence is not disclosed.
        if (!isReporter(user, defect)) {
            throw ApiException.notFound("Defect " + defect.getId() + " was not found");
        }
    }

    private static boolean isReporter(CurrentUser user, Defect defect) {
        return defect.getCreatedBy() != null && defect.getCreatedBy().equals(user.id());
    }
}

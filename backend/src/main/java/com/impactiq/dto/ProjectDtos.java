package com.impactiq.dto;

import com.impactiq.entity.ProjectRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ProjectRequest(
            @NotBlank(message = "Project name is required") @Size(max = 120, message = "Project name must be at most 120 characters")
            String name,
            @Size(max = 1000, message = "Description must be at most 1000 characters")
            String description
    ) {
    }

    public record ComponentRequest(
            @NotBlank(message = "Component name is required") @Size(max = 120, message = "Component name must be at most 120 characters")
            String name,
            @Size(max = 1000, message = "Description must be at most 1000 characters")
            String description
    ) {
    }

    public record MemberRequest(
            @NotBlank(message = "Username is required") @Size(max = 64) String username,
            @NotNull(message = "Role is required") ProjectRole role
    ) {
    }

    public record MemberRoleRequest(@NotNull(message = "Role is required") ProjectRole role) {
    }

    public record ComponentResponse(Long id, Long projectId, String name, String description) {
    }

    /** Result of an AI component discovery run; {@code added} is empty when nothing new was found. */
    public record ComponentDiscoveryResponse(List<ComponentResponse> added, int documentsRead, String message) {
    }

    public record ProjectResponse(
            Long id,
            String name,
            String description,
            ProjectRole myRole,
            List<ComponentResponse> components,
            long documentCount,
            long indexedDocumentCount,
            long chunkCount,
            Instant createdAt
    ) {
    }

    public record MemberResponse(Long userId, String username, String displayName, ProjectRole role, Instant addedAt) {
    }

    /** A registered account, listed for administrators when adding project members. */
    public record UserSummary(Long id, String username, String displayName, String role, boolean enabled, Instant createdAt) {
    }

    public record AuditEventResponse(
            Long id,
            String action,
            String actorUsername,
            String entityType,
            Long entityId,
            String detail,
            Instant createdAt
    ) {
    }
}

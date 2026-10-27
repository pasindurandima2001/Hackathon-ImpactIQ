package com.impactiq.controller;

import com.impactiq.dto.ProjectDtos.AuditEventResponse;
import com.impactiq.dto.ProjectDtos.ComponentDiscoveryResponse;
import com.impactiq.dto.ProjectDtos.ComponentRequest;
import com.impactiq.dto.ProjectDtos.ComponentResponse;
import com.impactiq.dto.ProjectDtos.MemberRequest;
import com.impactiq.dto.ProjectDtos.MemberResponse;
import com.impactiq.dto.ProjectDtos.MemberRoleRequest;
import com.impactiq.dto.ProjectDtos.ProjectRequest;
import com.impactiq.dto.ProjectDtos.ProjectResponse;
import com.impactiq.security.CurrentUserService;
import com.impactiq.service.ComponentDiscoveryService;
import com.impactiq.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final CurrentUserService currentUser;
    private final ComponentDiscoveryService componentDiscoveryService;

    public ProjectController(
            ProjectService projectService,
            CurrentUserService currentUser,
            ComponentDiscoveryService componentDiscoveryService
    ) {
        this.projectService = projectService;
        this.currentUser = currentUser;
        this.componentDiscoveryService = componentDiscoveryService;
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projectService.list(currentUser.require());
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(currentUser.require(), request));
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@PathVariable Long projectId) {
        return projectService.get(currentUser.require(), projectId);
    }

    @PutMapping("/{projectId}")
    public ProjectResponse update(@PathVariable Long projectId, @Valid @RequestBody ProjectRequest request) {
        return projectService.update(currentUser.require(), projectId, request);
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@PathVariable Long projectId) {
        projectService.delete(currentUser.require(), projectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/components")
    public ResponseEntity<ComponentResponse> addComponent(
            @PathVariable Long projectId,
            @Valid @RequestBody ComponentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.addComponent(currentUser.require(), projectId, request));
    }

    /** Runs AI component detection over the project's indexed documents and adds the new components. */
    @PostMapping("/{projectId}/components/discover")
    public ComponentDiscoveryResponse discoverComponents(@PathVariable Long projectId) {
        return componentDiscoveryService.discoverForProject(currentUser.require(), projectId);
    }

    @DeleteMapping("/{projectId}/components/{componentId}")
    public ResponseEntity<Void> deleteComponent(@PathVariable Long projectId, @PathVariable Long componentId) {
        projectService.deleteComponent(currentUser.require(), projectId, componentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/members")
    public List<MemberResponse> members(@PathVariable Long projectId) {
        return projectService.members(currentUser.require(), projectId);
    }

    @PostMapping("/{projectId}/members")
    public ResponseEntity<MemberResponse> addMember(@PathVariable Long projectId, @Valid @RequestBody MemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(projectService.addMember(currentUser.require(), projectId, request));
    }

    @PutMapping("/{projectId}/members/{userId}")
    public MemberResponse updateMember(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @Valid @RequestBody MemberRoleRequest request
    ) {
        return projectService.updateMemberRole(currentUser.require(), projectId, userId, request.role());
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long projectId, @PathVariable Long userId) {
        projectService.removeMember(currentUser.require(), projectId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/audit")
    public List<AuditEventResponse> audit(@PathVariable Long projectId) {
        return projectService.audit(currentUser.require(), projectId);
    }
}

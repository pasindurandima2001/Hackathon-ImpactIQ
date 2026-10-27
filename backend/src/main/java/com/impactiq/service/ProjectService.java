package com.impactiq.service;

import com.impactiq.dto.ProjectDtos.AuditEventResponse;
import com.impactiq.dto.ProjectDtos.ComponentRequest;
import com.impactiq.dto.ProjectDtos.ComponentResponse;
import com.impactiq.dto.ProjectDtos.MemberRequest;
import com.impactiq.dto.ProjectDtos.MemberResponse;
import com.impactiq.dto.ProjectDtos.ProjectRequest;
import com.impactiq.dto.ProjectDtos.ProjectResponse;
import com.impactiq.dto.ProjectDtos.UserSummary;
import com.impactiq.entity.AppUser;
import com.impactiq.entity.DocumentStatus;
import com.impactiq.entity.DocumentVersion;
import com.impactiq.entity.Project;
import com.impactiq.entity.ProjectComponent;
import com.impactiq.entity.ProjectMember;
import com.impactiq.entity.ProjectRole;
import com.impactiq.exception.ApiException;
import com.impactiq.rag.DocumentStorageService;
import com.impactiq.repository.AppUserRepository;
import com.impactiq.repository.AuditEventRepository;
import com.impactiq.repository.ChatConversationRepository;
import com.impactiq.repository.ChatMessageRepository;
import com.impactiq.repository.DefectRepository;
import com.impactiq.repository.DocumentChunkRepository;
import com.impactiq.repository.DocumentVersionRepository;
import com.impactiq.repository.KnowledgeDocumentRepository;
import com.impactiq.repository.ProjectComponentRepository;
import com.impactiq.repository.ProjectMemberRepository;
import com.impactiq.repository.ProjectRepository;
import com.impactiq.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectComponentRepository componentRepository;
    private final ProjectMemberRepository memberRepository;
    private final AppUserRepository userRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final AuditEventRepository auditEventRepository;
    private final AccessControlService accessControl;
    private final AuditService auditService;
    private final DefectRepository defectRepository;
    private final DocumentVersionRepository versionRepository;
    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final DocumentStorageService storageService;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectComponentRepository componentRepository,
            ProjectMemberRepository memberRepository,
            AppUserRepository userRepository,
            KnowledgeDocumentRepository documentRepository,
            DocumentChunkRepository chunkRepository,
            AuditEventRepository auditEventRepository,
            AccessControlService accessControl,
            AuditService auditService,
            DefectRepository defectRepository,
            DocumentVersionRepository versionRepository,
            ChatConversationRepository conversationRepository,
            ChatMessageRepository messageRepository,
            DocumentStorageService storageService
    ) {
        this.defectRepository = defectRepository;
        this.versionRepository = versionRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.storageService = storageService;
        this.projectRepository = projectRepository;
        this.componentRepository = componentRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.auditEventRepository = auditEventRepository;
        this.accessControl = accessControl;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(CurrentUser user) {
        return projectRepository.findAllById(accessControl.visibleProjectIds(user)).stream()
                .sorted(Comparator.comparing(Project::getName, String.CASE_INSENSITIVE_ORDER))
                .map(project -> toResponse(project, accessControl.roleFor(user, project.getId()).orElse(ProjectRole.VIEWER)))
                .toList();
    }

    public ProjectResponse create(CurrentUser user, ProjectRequest request) {
        accessControl.requireAdmin(user);
        String name = request.name().trim();
        if (projectRepository.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A project with that name already exists");
        }
        Project project = new Project();
        project.setName(name);
        project.setDescription(blankToNull(request.description()));
        project.setCreatedBy(user.id());
        Project saved = projectRepository.saveAndFlush(project);

        ProjectMember owner = new ProjectMember();
        owner.setProjectId(saved.getId());
        owner.setUserId(user.id());
        owner.setRole(ProjectRole.OWNER);
        memberRepository.save(owner);
        auditService.record(user, "PROJECT_CREATED", "PROJECT", saved.getId(), saved.getId(), saved.getName());
        return toResponse(saved, ProjectRole.OWNER);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(CurrentUser user, Long projectId) {
        ProjectRole role = accessControl.requireProjectRole(user, projectId, ProjectRole.VIEWER);
        return toResponse(findProject(projectId), role);
    }

    public ProjectResponse update(CurrentUser user, Long projectId, ProjectRequest request) {
        accessControl.requireAdmin(user);
        ProjectRole role = accessControl.requireProjectRole(user, projectId, ProjectRole.OWNER);
        Project project = findProject(projectId);
        String name = request.name().trim();
        if (projectRepository.existsByNameIgnoreCaseAndIdNot(name, projectId)) {
            throw ApiException.conflict("A project with that name already exists");
        }
        String previousName = project.getName();
        project.setName(name);
        project.setDescription(blankToNull(request.description()));
        Project saved = projectRepository.saveAndFlush(project);
        auditService.record(user, "PROJECT_UPDATED", "PROJECT", projectId, projectId,
                previousName.equals(name) ? name : previousName + " -> " + name);
        return toResponse(saved, role);
    }

    /**
     * Deletes the project with its knowledge (documents, versions, chunks, stored files), components, members and
     * chat conversations. Refused while defects still belong to the project: unscoping them would expose them to
     * every signed-in user, and deleting them silently would lose investigation history.
     */
    public void delete(CurrentUser user, Long projectId) {
        accessControl.requireAdmin(user);
        accessControl.requireProjectRole(user, projectId, ProjectRole.OWNER);
        Project project = findProject(projectId);
        long defects = defectRepository.countByProjectId(projectId);
        if (defects > 0) {
            throw ApiException.conflict("This project still has " + defects + " defect(s). Delete them first.");
        }
        List<String> storedNames = versionRepository.findByProjectId(projectId).stream()
                .map(DocumentVersion::getStoredFilename).toList();
        List<Long> conversationIds = conversationRepository.findIdsByProjectId(projectId);
        if (!conversationIds.isEmpty()) {
            messageRepository.deleteByConversationIdIn(conversationIds);
            conversationRepository.deleteByIdIn(conversationIds);
        }
        int chunks = chunkRepository.deleteByProjectId(projectId);
        versionRepository.deleteByProjectId(projectId);
        int documents = documentRepository.deleteByProjectId(projectId);
        componentRepository.deleteByProjectId(projectId);
        memberRepository.deleteByProjectId(projectId);
        projectRepository.delete(project);
        // Files are removed only after the database delete commits, so a rollback never loses originals.
        runAfterCommit(() -> storedNames.forEach(name -> storageService.delete(projectId, name)));
        auditService.record(user, "PROJECT_DELETED", "PROJECT", projectId, projectId,
                project.getName() + " documents=" + documents + " chunks=" + chunks + " chats=" + conversationIds.size());
    }

    public ComponentResponse addComponent(CurrentUser user, Long projectId, ComponentRequest request) {
        accessControl.requireAdmin(user);
        accessControl.requireProjectRole(user, projectId, ProjectRole.EDITOR);
        String name = request.name().trim();
        if (componentRepository.existsByProjectIdAndNameIgnoreCase(projectId, name)) {
            throw ApiException.conflict("A component with that name already exists in this project");
        }
        ProjectComponent component = new ProjectComponent();
        component.setProjectId(projectId);
        component.setName(name);
        component.setDescription(blankToNull(request.description()));
        ProjectComponent saved = componentRepository.saveAndFlush(component);
        auditService.record(user, "COMPONENT_CREATED", "COMPONENT", saved.getId(), projectId, saved.getName());
        return toResponse(saved);
    }

    public void deleteComponent(CurrentUser user, Long projectId, Long componentId) {
        accessControl.requireAdmin(user);
        accessControl.requireProjectRole(user, projectId, ProjectRole.OWNER);
        ProjectComponent component = requireComponent(projectId, componentId);
        boolean referenced = documentRepository.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
                .anyMatch(document -> componentId.equals(document.getComponentId()));
        if (referenced) {
            throw ApiException.conflict("Delete or move this component's documents first");
        }
        componentRepository.delete(component);
        auditService.record(user, "COMPONENT_DELETED", "COMPONENT", componentId, projectId, component.getName());
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> members(CurrentUser user, Long projectId) {
        accessControl.requireAdmin(user);
        accessControl.requireProjectRole(user, projectId, ProjectRole.VIEWER);
        List<ProjectMember> members = memberRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        Map<Long, AppUser> users = userRepository.findAllById(members.stream().map(ProjectMember::getUserId).toList())
                .stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));
        return members.stream().map(member -> toResponse(member, users.get(member.getUserId()))).toList();
    }

    public MemberResponse addMember(CurrentUser user, Long projectId, MemberRequest request) {
        accessControl.requireAdmin(user);
        accessControl.requireProjectRole(user, projectId, ProjectRole.OWNER);
        AppUser target = userRepository.findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(() -> ApiException.notFound("No user with that username"));
        if (memberRepository.findByProjectIdAndUserId(projectId, target.getId()).isPresent()) {
            throw ApiException.conflict("That user is already a project member");
        }
        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(target.getId());
        member.setRole(request.role());
        ProjectMember saved = memberRepository.saveAndFlush(member);
        auditService.record(user, "MEMBER_ADDED", "USER", target.getId(), projectId,
                target.getUsername() + " as " + request.role());
        return toResponse(saved, target);
    }

    public MemberResponse updateMemberRole(CurrentUser user, Long projectId, Long userId, ProjectRole role) {
        accessControl.requireAdmin(user);
        accessControl.requireProjectRole(user, projectId, ProjectRole.OWNER);
        ProjectMember member = memberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> ApiException.notFound("Member not found"));
        member.setRole(role);
        ProjectMember saved = memberRepository.saveAndFlush(member);
        auditService.record(user, "MEMBER_ROLE_CHANGED", "USER", userId, projectId, "role=" + role);
        return toResponse(saved, userRepository.findById(userId).orElse(null));
    }

    public void removeMember(CurrentUser user, Long projectId, Long userId) {
        accessControl.requireAdmin(user);
        accessControl.requireProjectRole(user, projectId, ProjectRole.OWNER);
        ProjectMember member = memberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> ApiException.notFound("Member not found"));
        memberRepository.delete(member);
        auditService.record(user, "MEMBER_REMOVED", "USER", userId, projectId, null);
    }

    /** Every registered account, so administrators can pick project members without typing usernames. */
    @Transactional(readOnly = true)
    public List<UserSummary> users(CurrentUser user) {
        accessControl.requireAdmin(user);
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(AppUser::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(account -> new UserSummary(account.getId(), account.getUsername(), account.getDisplayName(),
                        account.getRole().name(), account.isEnabled(), account.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> audit(CurrentUser user, Long projectId) {
        accessControl.requireAdmin(user);
        accessControl.requireProjectRole(user, projectId, ProjectRole.OWNER);
        return auditEventRepository.findTop100ByProjectIdOrderByCreatedAtDescIdDesc(projectId).stream()
                .map(event -> new AuditEventResponse(event.getId(), event.getAction(), event.getActorUsername(),
                        event.getEntityType(), event.getEntityId(), event.getDetail(), event.getCreatedAt()))
                .toList();
    }

    /** Validates that the component (when given) belongs to the project. */
    @Transactional(readOnly = true)
    public ProjectComponent requireComponent(Long projectId, Long componentId) {
        return componentRepository.findByIdAndProjectId(componentId, projectId)
                .orElseThrow(() -> ApiException.badRequest("Component does not belong to this project"));
    }

    @Transactional(readOnly = true)
    public Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("Project " + projectId + " was not found"));
    }

    private ProjectResponse toResponse(Project project, ProjectRole role) {
        List<ComponentResponse> components = componentRepository.findByProjectIdOrderByNameAsc(project.getId())
                .stream().map(this::toResponse).toList();
        long documents = documentRepository.findByProjectIdOrderByUpdatedAtDesc(project.getId()).size();
        long indexed = documentRepository.countByProjectIdAndStatus(project.getId(), DocumentStatus.INDEXED);
        return new ProjectResponse(project.getId(), project.getName(), project.getDescription(), role, components,
                documents, indexed, chunkRepository.countByProjectId(project.getId()), project.getCreatedAt());
    }

    private ComponentResponse toResponse(ProjectComponent component) {
        return new ComponentResponse(component.getId(), component.getProjectId(), component.getName(), component.getDescription());
    }

    private MemberResponse toResponse(ProjectMember member, AppUser user) {
        return new MemberResponse(member.getUserId(), user == null ? "unknown" : user.getUsername(),
                user == null ? null : user.getDisplayName(), member.getRole(), member.getCreatedAt());
    }

    private static void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

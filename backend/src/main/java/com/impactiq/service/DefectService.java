package com.impactiq.service;

import com.impactiq.dto.DefectRequest;
import com.impactiq.dto.DefectResponse;
import com.impactiq.dto.ImpactScoreBreakdown;
import com.impactiq.dto.ImpactScoreResult;
import com.impactiq.entity.Defect;
import com.impactiq.entity.DefectStatus;
import com.impactiq.entity.Project;
import com.impactiq.entity.ProjectComponent;
import com.impactiq.entity.ProjectRole;
import com.impactiq.exception.ApiException;
import com.impactiq.exception.DefectNotFoundException;
import com.impactiq.repository.DefectEvidenceRepository;
import com.impactiq.repository.DefectRepository;
import com.impactiq.repository.ImpactAnalysisRepository;
import com.impactiq.repository.InvestigationFeedbackRepository;
import com.impactiq.repository.InvestigationRepository;
import com.impactiq.repository.InvestigationStepRepository;
import com.impactiq.repository.ProjectComponentRepository;
import com.impactiq.repository.ProjectRepository;
import com.impactiq.security.CurrentUser;
import com.impactiq.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional
public class DefectService {

    private final DefectRepository defectRepository;
    private final ImpactAnalysisRepository impactAnalysisRepository;
    private final ImpactScoringService impactScoringService;
    private final DefectRelations relations;

    public DefectService(
            DefectRepository defectRepository,
            ImpactAnalysisRepository impactAnalysisRepository,
            ImpactScoringService impactScoringService,
            DefectRelations relations
    ) {
        this.defectRepository = defectRepository;
        this.impactAnalysisRepository = impactAnalysisRepository;
        this.impactScoringService = impactScoringService;
        this.relations = relations;
    }

    public DefectResponse create(DefectRequest request) {
        CurrentUser user = relations.currentUser().require();
        validateScope(user, request.projectId(), request.componentId());
        Defect defect = new Defect();
        applyRequest(defect, request);
        defect.setProjectId(request.projectId());
        defect.setComponentId(request.componentId());
        defect.setCreatedBy(user.id());
        if (request.status() == null) {
            defect.setStatus(DefectStatus.OPEN);
        }
        Defect saved = defectRepository.saveAndFlush(defect);
        relations.auditService().record(user, "DEFECT_CREATED", "DEFECT", saved.getId(), saved.getProjectId(), saved.getTitle());
        return toResponse(saved);
    }

    /** Only defects the caller may see: every defect for administrators, otherwise the defects the caller reported. */
    @Transactional(readOnly = true)
    public List<DefectResponse> findAll() {
        List<Defect> defects = visibleDefects(relations.currentUser().require());
        Map<Long, String> projects = relations.projectRepository()
                .findAllById(defects.stream().map(Defect::getProjectId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Project::getId, Project::getName));
        Map<Long, String> components = relations.componentRepository()
                .findAllById(defects.stream().map(Defect::getComponentId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProjectComponent::getId, ProjectComponent::getName));
        return defects.stream().map(defect -> toResponse(defect, projects, components)).toList();
    }

    @Transactional(readOnly = true)
    public List<Defect> visibleDefects(CurrentUser user) {
        if (user.admin()) {
            return defectRepository.findAll();
        }
        // Project defects stay hidden once the reporter is no longer a member of that project.
        return defectRepository.findByCreatedBy(user.id()).stream()
                .filter(defect -> relations.accessControl().canAccessDefect(user, defect, ProjectRole.VIEWER))
                .toList();
    }

    @Transactional(readOnly = true)
    public DefectResponse findById(Long id) {
        return toResponse(requireAccess(id, ProjectRole.VIEWER));
    }

    public DefectResponse update(Long id, DefectRequest request) {
        CurrentUser user = relations.currentUser().require();
        Defect defect = requireAccess(id, ProjectRole.EDITOR);
        if (!Objects.equals(defect.getProjectId(), request.projectId())) {
            validateScope(user, request.projectId(), request.componentId());
        } else if (request.componentId() != null && !Objects.equals(defect.getComponentId(), request.componentId())) {
            validateScope(user, request.projectId(), request.componentId());
        }
        applyRequest(defect, request);
        defect.setProjectId(request.projectId());
        defect.setComponentId(request.componentId());
        if (request.status() != null) {
            defect.setStatus(request.status());
        }
        return toResponse(defectRepository.saveAndFlush(defect));
    }

    /**
     * Removes the defect with its analysis history, evidence, investigations, stages and feedback in one transaction,
     * so no orphans remain even without database-level cascades.
     */
    public void delete(Long id) {
        Defect defect = findDefect(id);
        CurrentUser user = relations.currentUser().find().orElse(null);
        if (user != null) {
            relations.accessControl().requireDefectAccess(user, defect, ProjectRole.EDITOR);
        }
        List<Long> investigationIds = relations.investigationRepository().findIdsByDefectId(defect.getId());
        if (!investigationIds.isEmpty()) {
            relations.feedbackRepository().deleteByInvestigationIdIn(investigationIds);
            relations.stepRepository().deleteByInvestigationIdIn(investigationIds);
        }
        relations.investigationRepository().deleteByDefectId(defect.getId());
        relations.evidenceRepository().deleteByDefectId(defect.getId());
        impactAnalysisRepository.deleteByDefectId(defect.getId());
        defectRepository.delete(defect);
        relations.auditService().record(user, "DEFECT_DELETED", "DEFECT", id, defect.getProjectId(), defect.getTitle());
    }

    /** Loads a defect and enforces the caller's project role. Used by controllers of defect sub-resources. */
    @Transactional(readOnly = true)
    public Defect requireAccess(Long id, ProjectRole role) {
        Defect defect = findDefect(id);
        relations.accessControl().requireDefectAccess(relations.currentUser().require(), defect, role);
        return defect;
    }

    private void validateScope(CurrentUser user, Long projectId, Long componentId) {
        if (projectId == null) {
            if (componentId != null) {
                throw ApiException.badRequest("Select a project before selecting a component");
            }
            return;
        }
        // Every signed-in user may file a defect under any project (read access is enough).
        relations.accessControl().requireProjectRole(user, projectId, ProjectRole.VIEWER);
        if (componentId != null && relations.componentRepository().findByIdAndProjectId(componentId, projectId).isEmpty()) {
            throw ApiException.badRequest("Component does not belong to the selected project");
        }
    }

    private Defect findDefect(Long id) {
        return defectRepository.findById(id).orElseThrow(() -> new DefectNotFoundException(id));
    }

    private void applyRequest(Defect defect, DefectRequest request) {
        defect.setTitle(request.title());
        defect.setDescription(request.description());
        defect.setEnvironment(request.environment());
        defect.setService(request.service());
        defect.setBusinessFunction(request.businessFunction());
        defect.setAffectedUsers(request.affectedUsers());
        defect.setFrequency(request.frequency());
        defect.setBusinessCriticality(request.businessCriticality());
        defect.setCustomerImpact(request.customerImpact());
        defect.setTechnicalImpact(request.technicalImpact());
        if (request.status() != null) {
            defect.setStatus(request.status());
        }
    }

    private DefectResponse toResponse(Defect defect) {
        Map<Long, String> projects = defect.getProjectId() == null ? Map.of()
                : relations.projectRepository().findById(defect.getProjectId())
                .map(project -> Map.of(project.getId(), project.getName())).orElse(Map.of());
        Map<Long, String> components = defect.getComponentId() == null ? Map.of()
                : relations.componentRepository().findById(defect.getComponentId())
                .map(component -> Map.of(component.getId(), component.getName())).orElse(Map.of());
        return toResponse(defect, projects, components);
    }

    private DefectResponse toResponse(Defect defect, Map<Long, String> projects, Map<Long, String> components) {
        ImpactScoreResult baseline = impactScoringService.calculate(defect);
        return new DefectResponse(
                defect.getId(),
                defect.getTitle(),
                defect.getDescription(),
                defect.getEnvironment(),
                defect.getService(),
                defect.getBusinessFunction(),
                defect.getAffectedUsers(),
                defect.getFrequency(),
                defect.getBusinessCriticality(),
                defect.getCustomerImpact(),
                defect.getTechnicalImpact(),
                defect.getStatus(),
                defect.getCreatedAt(),
                defect.getUpdatedAt(),
                baseline.totalScore(),
                baseline.riskLevel(),
                baseline.priority(),
                defect.getProjectId(),
                defect.getProjectId() == null ? null : projects.get(defect.getProjectId()),
                defect.getComponentId(),
                defect.getComponentId() == null ? null : components.get(defect.getComponentId()),
                new ImpactScoreBreakdown(
                        baseline.userImpactScore(),
                        baseline.businessImpactScore(),
                        baseline.customerImpactScore(),
                        baseline.technicalImpactScore())
        );
    }
}

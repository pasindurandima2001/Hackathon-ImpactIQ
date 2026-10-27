package com.impactiq.service;

import com.impactiq.repository.DefectEvidenceRepository;
import com.impactiq.repository.InvestigationFeedbackRepository;
import com.impactiq.repository.InvestigationRepository;
import com.impactiq.repository.InvestigationStepRepository;
import com.impactiq.repository.ProjectComponentRepository;
import com.impactiq.repository.ProjectRepository;
import com.impactiq.security.CurrentUserService;
import org.springframework.stereotype.Component;

/** Collaborators DefectService needs for project scoping, authorization, cascade deletes and auditing. */
@Component
public record DefectRelations(
        CurrentUserService currentUser,
        AccessControlService accessControl,
        ProjectRepository projectRepository,
        ProjectComponentRepository componentRepository,
        DefectEvidenceRepository evidenceRepository,
        InvestigationRepository investigationRepository,
        InvestigationStepRepository stepRepository,
        InvestigationFeedbackRepository feedbackRepository,
        AuditService auditService
) {
}

package com.impactiq.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactiq.agent.InvestigationOrchestrator;
import com.impactiq.config.InvestigationProperties;
import com.impactiq.dto.ImpactScoreResult;
import com.impactiq.dto.InvestigationDtos.FeedbackRequest;
import com.impactiq.dto.InvestigationDtos.FeedbackResponse;
import com.impactiq.dto.InvestigationDtos.InvestigationResponse;
import com.impactiq.dto.InvestigationDtos.InvestigationSummary;
import com.impactiq.dto.InvestigationDtos.StepResponse;
import com.impactiq.entity.Defect;
import com.impactiq.entity.FeedbackVerdict;
import com.impactiq.entity.Investigation;
import com.impactiq.entity.InvestigationFeedback;
import com.impactiq.entity.InvestigationStatus;
import com.impactiq.entity.InvestigationStep;
import com.impactiq.entity.ProjectRole;
import com.impactiq.entity.StepStatus;
import com.impactiq.exception.ApiException;
import com.impactiq.exception.DefectNotFoundException;
import com.impactiq.repository.DefectRepository;
import com.impactiq.repository.InvestigationFeedbackRepository;
import com.impactiq.repository.InvestigationRepository;
import com.impactiq.repository.InvestigationStepRepository;
import com.impactiq.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Starts, retries and reads investigations. Concurrency rules: at most one active (QUEUED/RUNNING) run per
 * defect, enforced under a row lock on the defect, and a global cap on active runs.
 */
@Service
public class InvestigationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InvestigationService.class);

    private final InvestigationRepository investigationRepository;
    private final InvestigationStepRepository stepRepository;
    private final InvestigationFeedbackRepository feedbackRepository;
    private final DefectRepository defectRepository;
    private final AccessControlService accessControl;
    private final ImpactScoringService impactScoringService;
    private final InvestigationOrchestrator orchestrator;
    private final InvestigationProperties properties;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ThreadPoolTaskExecutor executor;

    public InvestigationService(
            InvestigationRepository investigationRepository,
            InvestigationStepRepository stepRepository,
            InvestigationFeedbackRepository feedbackRepository,
            DefectRepository defectRepository,
            AccessControlService accessControl,
            ImpactScoringService impactScoringService,
            InvestigationOrchestrator orchestrator,
            InvestigationProperties properties,
            AuditService auditService,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            @Qualifier("investigationExecutor") ThreadPoolTaskExecutor executor
    ) {
        this.investigationRepository = investigationRepository;
        this.stepRepository = stepRepository;
        this.feedbackRepository = feedbackRepository;
        this.defectRepository = defectRepository;
        this.accessControl = accessControl;
        this.impactScoringService = impactScoringService;
        this.orchestrator = orchestrator;
        this.properties = properties;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.executor = executor;
    }

    @Transactional
    public InvestigationResponse start(CurrentUser user, Long defectId) {
        Defect defect = lockDefect(user, defectId);
        Investigation investigation = createRun(user, defect, 1, null);
        auditService.record(user, "INVESTIGATION_STARTED", "INVESTIGATION", investigation.getId(), defect.getProjectId(),
                "defect=" + defectId);
        return toResponse(investigation);
    }

    @Transactional
    public InvestigationResponse retry(CurrentUser user, Long defectId, Long investigationId) {
        Defect defect = lockDefect(user, defectId);
        Investigation previous = findRun(defectId, investigationId);
        if (!InvestigationStatus.RETRYABLE.contains(previous.getStatus())) {
            throw ApiException.conflict("Only FAILED or PARTIAL investigations can be retried");
        }
        if (previous.getAttempt() >= properties.getMaxAttempts()) {
            throw ApiException.conflict("Retry limit reached (" + properties.getMaxAttempts()
                    + " attempts). Add evidence and start a new investigation instead.");
        }
        Investigation retry = createRun(user, defect, previous.getAttempt() + 1, previous.getId());
        auditService.record(user, "INVESTIGATION_RETRIED", "INVESTIGATION", retry.getId(), defect.getProjectId(),
                "retryOf=" + previous.getId() + " attempt=" + retry.getAttempt());
        return toResponse(retry);
    }

    @Transactional(readOnly = true)
    public List<InvestigationSummary> list(CurrentUser user, Long defectId) {
        requireDefect(user, defectId, ProjectRole.VIEWER);
        return investigationRepository.findByDefectIdOrderByCreatedAtDescIdDesc(defectId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public InvestigationResponse get(CurrentUser user, Long defectId, Long investigationId) {
        requireDefect(user, defectId, ProjectRole.VIEWER);
        return toResponse(findRun(defectId, investigationId));
    }

    /** Any project member may comment; confirming or rejecting a hypothesis requires EDITOR. */
    @Transactional
    public FeedbackResponse addFeedback(CurrentUser user, Long defectId, Long investigationId, FeedbackRequest request) {
        boolean decisive = request.verdict() == FeedbackVerdict.CONFIRMED || request.verdict() == FeedbackVerdict.REJECTED;
        Defect defect = requireDefect(user, defectId, decisive ? ProjectRole.EDITOR : ProjectRole.VIEWER);
        Investigation investigation = findRun(defectId, investigationId);
        if (InvestigationStatus.ACTIVE.contains(investigation.getStatus())) {
            throw ApiException.conflict("Wait for the investigation to finish before giving feedback");
        }
        if (request.hypothesisRank() != null && request.hypothesisRank() > hypothesisCount(investigation)) {
            throw ApiException.badRequest("Hypothesis rank " + request.hypothesisRank() + " does not exist in this report");
        }
        if (decisive && request.hypothesisRank() == null) {
            throw ApiException.badRequest("Choose the hypothesis you are confirming or rejecting");
        }
        InvestigationFeedback feedback = new InvestigationFeedback();
        feedback.setInvestigationId(investigationId);
        feedback.setHypothesisRank(request.hypothesisRank());
        feedback.setVerdict(request.verdict());
        feedback.setComment(request.comment() == null || request.comment().isBlank() ? null : request.comment().trim());
        feedback.setUserId(user.id());
        feedback.setUsername(user.username());
        InvestigationFeedback saved = feedbackRepository.saveAndFlush(feedback);
        auditService.record(user, "INVESTIGATION_FEEDBACK", "INVESTIGATION", investigationId, defect.getProjectId(),
                request.verdict() + (request.hypothesisRank() == null ? "" : " rank=" + request.hypothesisRank()));
        return toResponse(saved);
    }

    /** Runs left QUEUED/RUNNING by a shutdown or crash cannot resume mid-call; mark them retryable failures. */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        transactionTemplate.executeWithoutResult(status -> {
            List<Investigation> interrupted = investigationRepository.findByStatusIn(InvestigationStatus.ACTIVE);
            for (Investigation investigation : interrupted) {
                investigation.setStatus(InvestigationStatus.FAILED);
                investigation.setErrorCategory("INTERRUPTED");
                investigation.setErrorMessage("The server restarted while this investigation was running. Retry to continue.");
                investigation.setCompletedAt(Instant.now());
                stepRepository.findByInvestigationIdOrderBySequenceNumberAsc(investigation.getId()).stream()
                        .filter(step -> step.getStatus() == StepStatus.PENDING || step.getStatus() == StepStatus.RUNNING)
                        .forEach(step -> {
                            step.setStatus(StepStatus.SKIPPED);
                            step.setErrorMessage("Interrupted by server restart");
                        });
            }
            if (!interrupted.isEmpty()) {
                LOGGER.info("Marked {} interrupted investigation(s) as FAILED (retry eligible)", interrupted.size());
            }
        });
    }

    private Defect lockDefect(CurrentUser user, Long defectId) {
        Defect defect = defectRepository.findByIdForUpdate(defectId).orElseThrow(() -> new DefectNotFoundException(defectId));
        accessControl.requireDefectAccess(user, defect, ProjectRole.EDITOR);
        if (investigationRepository.existsByDefectIdAndStatusIn(defectId, InvestigationStatus.ACTIVE)) {
            throw ApiException.conflict("An investigation is already running for this defect");
        }
        if (investigationRepository.countByStatusIn(InvestigationStatus.ACTIVE) >= properties.getMaxActive()) {
            throw ApiException.tooManyRequests("The investigation queue is busy. Try again in a minute.");
        }
        return defect;
    }

    private Investigation createRun(CurrentUser user, Defect defect, int attempt, Long parentId) {
        ImpactScoreResult baseline = impactScoringService.calculate(defect);
        Investigation investigation = new Investigation();
        investigation.setDefectId(defect.getId());
        investigation.setProjectId(defect.getProjectId());
        investigation.setComponentId(defect.getComponentId());
        investigation.setStatus(InvestigationStatus.QUEUED);
        investigation.setAttempt(attempt);
        investigation.setParentInvestigationId(parentId);
        investigation.setTriggeredBy(user.id());
        investigation.setEngine(orchestrator.engine());
        investigation.setModel(orchestrator.model());
        investigation.setBaselineScore(baseline.totalScore());
        investigation.setBaselineRiskLevel(baseline.riskLevel());
        investigation.setBaselinePriority(baseline.priority());
        Investigation saved = investigationRepository.saveAndFlush(investigation);

        int sequence = 1;
        for (String stage : InvestigationOrchestrator.STAGES) {
            InvestigationStep step = new InvestigationStep();
            step.setInvestigationId(saved.getId());
            step.setAgentName(stage);
            step.setSequenceNumber(sequence++);
            step.setStatus(StepStatus.PENDING);
            stepRepository.save(step);
        }
        submitAfterCommit(saved.getId(), user);
        return saved;
    }

    private void submitAfterCommit(Long investigationId, CurrentUser user) {
        Runnable submit = () -> {
            try {
                executor.execute(() -> orchestrator.run(investigationId, user));
            } catch (TaskRejectedException exception) {
                transactionTemplate.executeWithoutResult(status -> investigationRepository.findById(investigationId)
                        .ifPresent(investigation -> {
                            investigation.setStatus(InvestigationStatus.FAILED);
                            investigation.setErrorCategory("QUEUE_FULL");
                            investigation.setErrorMessage("The investigation queue was full. Retry shortly.");
                            investigation.setCompletedAt(Instant.now());
                        }));
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit.run();
                }
            });
        } else {
            submit.run();
        }
    }

    private Defect requireDefect(CurrentUser user, Long defectId, ProjectRole role) {
        Defect defect = defectRepository.findById(defectId).orElseThrow(() -> new DefectNotFoundException(defectId));
        accessControl.requireDefectAccess(user, defect, role);
        return defect;
    }

    private Investigation findRun(Long defectId, Long investigationId) {
        return investigationRepository.findByIdAndDefectId(investigationId, defectId)
                .orElseThrow(() -> ApiException.notFound("Investigation " + investigationId + " was not found"));
    }

    private InvestigationResponse toResponse(Investigation investigation) {
        List<StepResponse> steps = stepRepository.findByInvestigationIdOrderBySequenceNumberAsc(investigation.getId()).stream()
                .map(step -> new StepResponse(step.getId(), step.getAgentName(),
                        InvestigationOrchestrator.DISPLAY_NAMES.getOrDefault(step.getAgentName(), step.getAgentName()),
                        step.getSequenceNumber(), step.getStatus(), step.getAttempts(), step.getDurationMs(),
                        step.getErrorCategory(), step.getErrorMessage(), step.getSummary(), step.getStartedAt(),
                        step.getCompletedAt()))
                .toList();
        List<FeedbackResponse> feedback = feedbackRepository.findByInvestigationIdOrderByCreatedAtDescIdDesc(investigation.getId())
                .stream().map(this::toResponse).toList();
        return new InvestigationResponse(toSummary(investigation), steps, readReport(investigation), feedback);
    }

    public InvestigationSummary toSummary(Investigation investigation) {
        JsonNode report = readReport(investigation);
        JsonNode hypotheses = report == null ? null : report.path("hypotheses");
        int count = hypotheses == null || !hypotheses.isArray() ? 0 : hypotheses.size();
        String top = count == 0 ? null : hypotheses.get(0).path("title").asText(null);
        boolean retryEligible = InvestigationStatus.RETRYABLE.contains(investigation.getStatus())
                && investigation.getAttempt() < properties.getMaxAttempts();
        return new InvestigationSummary(investigation.getId(), investigation.getDefectId(), investigation.getStatus(),
                investigation.getAttempt(), investigation.getParentInvestigationId(), investigation.getEngine(),
                investigation.getModel(), investigation.getBaselineScore(), investigation.getBaselineRiskLevel(),
                investigation.getBaselinePriority(), investigation.getErrorCategory(), investigation.getErrorMessage(),
                retryEligible, count, top,
                feedbackRepository.findByInvestigationIdOrderByCreatedAtDescIdDesc(investigation.getId()).size(),
                investigation.getCreatedAt(), investigation.getStartedAt(), investigation.getCompletedAt());
    }

    private int hypothesisCount(Investigation investigation) {
        JsonNode report = readReport(investigation);
        return report == null ? 0 : report.path("hypotheses").size();
    }

    private JsonNode readReport(Investigation investigation) {
        if (investigation.getReportJson() == null) {
            return null;
        }
        try {
            return objectMapper.readTree(investigation.getReportJson());
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Stored report for investigation {} is unreadable", investigation.getId());
            return null;
        }
    }

    private FeedbackResponse toResponse(InvestigationFeedback feedback) {
        return new FeedbackResponse(feedback.getId(), feedback.getInvestigationId(), feedback.getHypothesisRank(),
                feedback.getVerdict(), feedback.getComment(), feedback.getUsername(), feedback.getCreatedAt());
    }
}

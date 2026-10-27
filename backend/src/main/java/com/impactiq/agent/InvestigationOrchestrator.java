package com.impactiq.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactiq.agent.AgentContracts.EvidenceAnalysisOutput;
import com.impactiq.agent.AgentContracts.EvidenceItem;
import com.impactiq.agent.AgentContracts.FixOutput;
import com.impactiq.agent.AgentContracts.Hypothesis;
import com.impactiq.agent.AgentContracts.MlSignal;
import com.impactiq.agent.AgentContracts.ProjectContextOutput;
import com.impactiq.agent.AgentContracts.RootCauseOutput;
import com.impactiq.agent.AgentContracts.SourceRef;
import com.impactiq.agent.AgentContracts.TriageOutput;
import com.impactiq.config.InvestigationProperties;
import com.impactiq.dto.ImpactScoreBreakdown;
import com.impactiq.dto.ImpactScoreResult;
import com.impactiq.dto.InvestigationReport;
import com.impactiq.entity.Defect;
import com.impactiq.entity.DefectEvidence;
import com.impactiq.entity.Investigation;
import com.impactiq.entity.InvestigationStatus;
import com.impactiq.entity.InvestigationStep;
import com.impactiq.entity.StepStatus;
import com.impactiq.rag.FileValidationService;
import com.impactiq.rag.PromptInjectionDetector;
import com.impactiq.rag.SecretRedactor;
import com.impactiq.repository.DefectEvidenceRepository;
import com.impactiq.repository.DefectRepository;
import com.impactiq.repository.InvestigationRepository;
import com.impactiq.repository.InvestigationStepRepository;
import com.impactiq.repository.ProjectComponentRepository;
import com.impactiq.repository.ProjectRepository;
import com.impactiq.security.CurrentUser;
import com.impactiq.service.ImpactScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Root Orchestrator Agent. Coordinates the specialists in dependency order
 * (Project Context → [Defect Triage ∥ Evidence Analysis] → Root Cause Analysis → Fix Recommendation),
 * persists every stage, reuses successful stages on retry when their inputs are unchanged, applies the
 * time budget, decides the final status, and assembles the cited report.
 *
 * <p>Failure policy: Triage, Evidence Analysis and Fix Recommendation are non-critical (the run becomes PARTIAL);
 * Root Cause Analysis is critical (the run becomes FAILED). Missing credentials or an exhausted time budget
 * stop the remaining model stages.</p>
 */
@Component
public class InvestigationOrchestrator {

    public static final String NAME = "root_orchestrator_agent";
    public static final List<String> STAGES = List.of(
            "project_context_agent",
            "defect_triage_agent",
            "evidence_analysis_agent",
            "root_cause_analysis_agent",
            "fix_recommendation_agent",
            NAME
    );
    public static final Map<String, String> DISPLAY_NAMES = Map.of(
            "project_context_agent", "Project Context Agent",
            "defect_triage_agent", "Defect Triage Agent",
            "evidence_analysis_agent", "Evidence Analysis Agent",
            "root_cause_analysis_agent", "Root Cause Analysis Agent",
            "fix_recommendation_agent", "Fix Recommendation Agent",
            NAME, "Root Orchestrator Agent"
    );

    private static final Logger LOGGER = LoggerFactory.getLogger(InvestigationOrchestrator.class);
    private static final int MAX_ERROR_LENGTH = 500;
    private static final int MIN_ITEM_BUDGET = 200;
    private static final int RETRIEVAL_QUERY_DESCRIPTION_CHARS = 1500;
    private static final int RETRIEVAL_QUERY_SIGNAL_LINES = 10;
    /** A virtual thread per parallel stage: it only waits on the model call, and each run is already bounded upstream. */
    private static final Executor PARALLEL_STAGE_EXECUTOR = task -> Thread.ofVirtual().name("investigation-stage").start(task);

    private final InvestigationRepository investigationRepository;
    private final InvestigationStepRepository stepRepository;
    private final DefectRepository defectRepository;
    private final DefectEvidenceRepository evidenceRepository;
    private final ProjectRepository projectRepository;
    private final ProjectComponentRepository componentRepository;
    private final ImpactScoringService impactScoringService;
    private final ProjectContextAgent projectContextAgent;
    private final DefectTriageAgent triageAgent;
    private final EvidenceAnalysisAgent evidenceAgent;
    private final RootCauseAnalysisAgent rootCauseAgent;
    private final FixRecommendationAgent fixAgent;
    private final MlTriageClient mlTriageClient;
    private final StructuredAgentInvoker invoker;
    private final SecretRedactor secretRedactor;
    private final PromptInjectionDetector injectionDetector;
    private final InvestigationProperties properties;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public InvestigationOrchestrator(
            InvestigationRepository investigationRepository,
            InvestigationStepRepository stepRepository,
            DefectRepository defectRepository,
            DefectEvidenceRepository evidenceRepository,
            ProjectRepository projectRepository,
            ProjectComponentRepository componentRepository,
            ImpactScoringService impactScoringService,
            ProjectContextAgent projectContextAgent,
            DefectTriageAgent triageAgent,
            EvidenceAnalysisAgent evidenceAgent,
            RootCauseAnalysisAgent rootCauseAgent,
            FixRecommendationAgent fixAgent,
            MlTriageClient mlTriageClient,
            StructuredAgentInvoker invoker,
            SecretRedactor secretRedactor,
            PromptInjectionDetector injectionDetector,
            InvestigationProperties properties,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate
    ) {
        this.investigationRepository = investigationRepository;
        this.stepRepository = stepRepository;
        this.defectRepository = defectRepository;
        this.evidenceRepository = evidenceRepository;
        this.projectRepository = projectRepository;
        this.componentRepository = componentRepository;
        this.impactScoringService = impactScoringService;
        this.projectContextAgent = projectContextAgent;
        this.triageAgent = triageAgent;
        this.evidenceAgent = evidenceAgent;
        this.rootCauseAgent = rootCauseAgent;
        this.fixAgent = fixAgent;
        this.mlTriageClient = mlTriageClient;
        this.invoker = invoker;
        this.secretRedactor = secretRedactor;
        this.injectionDetector = injectionDetector;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public String engine() {
        return invoker.engine();
    }

    public String model() {
        return invoker.model();
    }

    /** Entry point for the background executor. Never throws: every failure is persisted on the run. */
    public void run(Long investigationId, CurrentUser user) {
        long started = System.currentTimeMillis();
        Instant deadline = Instant.now().plusMillis(properties.getRunTimeoutMs());
        RunState state = new RunState();
        try {
            Investigation investigation = transactionTemplate.execute(status -> markRunning(investigationId));
            if (investigation == null) {
                return;
            }
            Defect defect = defectRepository.findById(investigation.getDefectId()).orElse(null);
            if (defect == null) {
                finish(investigationId, InvestigationStatus.FAILED, "DEFECT_DELETED", "The defect was deleted", null);
                return;
            }
            ImpactScoreResult baseline = impactScoringService.calculate(defect);
            Map<String, JsonNode> reusable = reusableOutputs(investigation.getParentInvestigationId());
            DefectSnapshot snapshot = snapshot(defect, baseline);

            // 1. Project Context (deterministic, authorised retrieval)
            ProjectContextOutput context = runStage(investigationId, "project_context_agent", state, null, ProjectContextOutput.class,
                    () -> projectContextAgent.run(new ProjectContextAgent.Input(defect.getProjectId(), defect.getComponentId(),
                            user, retrievalQuery(defect)), deadline));

            List<EvidenceItem> catalog = buildCatalog(defect, context, state);
            // Optional trained-model prediction: an advisory signal for Triage, part of the reuse fingerprint when present
            MlSignal mlSignal = mlTriageClient.predict(snapshot.title(), snapshot.description()).orElse(null);
            String fingerprint = FileValidationService.sha256(json(snapshot) + json(catalog) + (mlSignal == null ? "" : json(mlSignal)));
            state.reuseChain = true;

            // 2 + 3. Defect Triage and Evidence Analysis (both non-critical) run in parallel. Evidence Analysis reads the
            // evidence catalog directly and does not need the triage output, so the two model calls overlap.
            // Both decide reuse from the same starting chain state, so the outcome does not depend on which finishes first.
            boolean reuseAllowed = state.reuseChain;
            CompletableFuture<EvidenceAnalysisOutput> evidenceFuture = CompletableFuture.supplyAsync(
                    () -> runModelStage(investigationId, "evidence_analysis_agent", state, reuseAllowed, reusable, fingerprint,
                            EvidenceAnalysisOutput.class,
                            () -> evidenceAgent.run(new EvidenceAnalysisAgent.Input(snapshot, null, catalog), deadline)),
                    PARALLEL_STAGE_EXECUTOR);
            TriageOutput triage;
            try {
                triage = runModelStage(investigationId, "defect_triage_agent", state, reuseAllowed, reusable, fingerprint,
                        TriageOutput.class, () -> triageAgent.run(new DefectTriageAgent.Input(snapshot, catalog, mlSignal), deadline));
            } finally {
                // Never leave the evidence stage writing to the run after this method has moved on.
                evidenceFuture.exceptionally(error -> null).join();
            }
            EvidenceAnalysisOutput findings = evidenceFuture.join();

            // 4. Root Cause Analysis (critical)
            RootCauseOutput rootCause = runModelStage(investigationId, "root_cause_analysis_agent", state, reusable, fingerprint,
                    RootCauseOutput.class,
                    () -> rootCauseAgent.run(new RootCauseAnalysisAgent.Input(snapshot, triage, findings, catalog), deadline));

            // 5. Fix Recommendation (depends on Root Cause)
            FixOutput fix = null;
            if (rootCause != null) {
                fix = runModelStage(investigationId, "fix_recommendation_agent", state, reusable, fingerprint, FixOutput.class,
                        () -> fixAgent.run(new FixRecommendationAgent.Input(snapshot, rootCause, catalog), deadline));
            } else {
                skip(investigationId, "fix_recommendation_agent", "Skipped because root cause analysis did not succeed");
                state.failedStages.add("Fix Recommendation Agent: skipped (no root-cause hypotheses)");
            }

            // 6. Root Orchestrator: assemble and validate the report
            InvestigationStatus finalStatus = rootCause == null ? InvestigationStatus.FAILED
                    : state.failedStages.isEmpty() ? InvestigationStatus.COMPLETED : InvestigationStatus.PARTIAL;
            long duration = System.currentTimeMillis() - started;
            InvestigationReport report = assemble(defect, baseline, context, catalog, triage, mlSignal, findings, rootCause, fix, state,
                    finalStatus, investigation.getAttempt(), duration);
            FixOutput finalFix = fix;
            runStage(investigationId, NAME, state, null, String.class, () -> new InvestigationAgent.AgentOutcome<>(
                    "Report assembled: " + (rootCause == null ? 0 : rootCause.hypotheses().size()) + " hypotheses, "
                            + (finalFix == null ? 0 : finalFix.fixSteps().size()) + " fix steps", 1, List.of()));
            finish(investigationId, finalStatus, finalStatus == InvestigationStatus.FAILED ? state.primaryErrorCategory() : null,
                    finalStatus == InvestigationStatus.FAILED ? state.primaryErrorMessage() : null, json(report));
        } catch (RuntimeException exception) {
            LOGGER.error("Investigation {} failed unexpectedly: category={}", investigationId, exception.getClass().getSimpleName());
            transactionTemplate.executeWithoutResult(status -> stepRepository.findByInvestigationIdOrderBySequenceNumberAsc(investigationId)
                    .stream()
                    .filter(step -> step.getStatus() == StepStatus.PENDING || step.getStatus() == StepStatus.RUNNING)
                    .forEach(step -> {
                        step.setStatus(StepStatus.SKIPPED);
                        step.setErrorMessage("Stopped after an internal error");
                    }));
            finish(investigationId, InvestigationStatus.FAILED, "INTERNAL", "The investigation failed unexpectedly. Retry to run it again.", null);
        }
    }

    private Investigation markRunning(Long investigationId) {
        Investigation investigation = investigationRepository.findById(investigationId).orElse(null);
        if (investigation == null || investigation.getStatus() != InvestigationStatus.QUEUED) {
            return null;
        }
        investigation.setStatus(InvestigationStatus.RUNNING);
        investigation.setStartedAt(Instant.now());
        investigation.setEngine(invoker.engine());
        investigation.setModel(invoker.model());
        return investigationRepository.saveAndFlush(investigation);
    }

    /** Runs a model-backed stage, reusing the parent run's output when the inputs and all earlier stages match. */
    private <T> T runModelStage(
            Long investigationId,
            String stage,
            RunState state,
            Map<String, JsonNode> reusable,
            String fingerprint,
            Class<T> type,
            Supplier<InvestigationAgent.AgentOutcome<T>> work
    ) {
        return runModelStage(investigationId, stage, state, state.reuseChain, reusable, fingerprint, type, work);
    }

    private <T> T runModelStage(
            Long investigationId,
            String stage,
            RunState state,
            boolean reuseAllowed,
            Map<String, JsonNode> reusable,
            String fingerprint,
            Class<T> type,
            Supplier<InvestigationAgent.AgentOutcome<T>> work
    ) {
        if (state.stopModelStages != null) {
            skip(investigationId, stage, state.stopModelStages);
            state.failedStages.add(DISPLAY_NAMES.get(stage) + ": skipped (" + state.stopModelStages + ")");
            return null;
        }
        JsonNode previous = reusable.get(stage);
        if (reuseAllowed && previous != null && fingerprint.equals(previous.path("fingerprint").asText())) {
            try {
                T output = objectMapper.treeToValue(previous.get("output"), type);
                updateStep(investigationId, stage, step -> {
                    step.setStatus(StepStatus.REUSED);
                    step.setAttempts(0);
                    step.setDurationMs(0L);
                    step.setStartedAt(Instant.now());
                    step.setCompletedAt(Instant.now());
                    step.setSummary("Reused unchanged output from the previous attempt");
                    step.setOutputJson(previous.toString());
                });
                return output;
            } catch (JsonProcessingException | IllegalArgumentException exception) {
                LOGGER.warn("Stored output for stage {} could not be reused; re-running", stage);
            }
        }
        state.reuseChain = false;
        return runStage(investigationId, stage, state, fingerprint, type, work);
    }

    private <T> T runStage(
            Long investigationId,
            String stage,
            RunState state,
            String fingerprint,
            Class<T> type,
            Supplier<InvestigationAgent.AgentOutcome<T>> work
    ) {
        Instant stepStart = Instant.now();
        updateStep(investigationId, stage, step -> {
            step.setStatus(StepStatus.RUNNING);
            step.setStartedAt(stepStart);
        });
        try {
            InvestigationAgent.AgentOutcome<T> outcome = work.get();
            state.warnings.addAll(outcome.warnings());
            String envelope = fingerprint == null ? json(Map.of("output", outcome.output()))
                    : json(Map.of("fingerprint", fingerprint, "output", outcome.output()));
            updateStep(investigationId, stage, step -> {
                step.setStatus(StepStatus.SUCCEEDED);
                step.setAttempts(outcome.attempts());
                step.setCompletedAt(Instant.now());
                step.setDurationMs(Duration.between(stepStart, Instant.now()).toMillis());
                step.setSummary(truncate(summarize(outcome.output()), MAX_ERROR_LENGTH));
                step.setOutputJson(envelope);
            });
            return outcome.output();
        } catch (AgentException exception) {
            state.recordFailure(DISPLAY_NAMES.get(stage), exception);
            if (exception.category() == AgentException.Category.MISSING_API_KEY
                    || exception.category() == AgentException.Category.AUTH_FAILED
                    || exception.category() == AgentException.Category.RUN_TIMEOUT) {
                state.stopModelStages = exception.getMessage();
            }
            updateStep(investigationId, stage, step -> {
                step.setStatus(StepStatus.FAILED);
                step.setAttempts(exception.attempts());
                step.setCompletedAt(Instant.now());
                step.setDurationMs(Duration.between(stepStart, Instant.now()).toMillis());
                step.setErrorCategory(exception.category().name());
                step.setErrorMessage(truncate(exception.getMessage(), MAX_ERROR_LENGTH));
            });
            return null;
        }
    }

    private void skip(Long investigationId, String stage, String reason) {
        updateStep(investigationId, stage, step -> {
            step.setStatus(StepStatus.SKIPPED);
            step.setErrorMessage(truncate(reason, MAX_ERROR_LENGTH));
            step.setCompletedAt(Instant.now());
        });
    }

    private void updateStep(Long investigationId, String stage, java.util.function.Consumer<InvestigationStep> change) {
        transactionTemplate.executeWithoutResult(status -> stepRepository.findByInvestigationIdOrderBySequenceNumberAsc(investigationId)
                .stream()
                .filter(step -> step.getAgentName().equals(stage))
                .findFirst()
                .ifPresent(step -> {
                    change.accept(step);
                    stepRepository.save(step);
                }));
    }

    private void finish(Long investigationId, InvestigationStatus status, String errorCategory, String errorMessage, String reportJson) {
        transactionTemplate.executeWithoutResult(tx -> investigationRepository.findById(investigationId).ifPresent(investigation -> {
            investigation.setStatus(status);
            investigation.setErrorCategory(errorCategory);
            investigation.setErrorMessage(truncate(errorMessage, MAX_ERROR_LENGTH));
            if (reportJson != null) {
                investigation.setReportJson(reportJson);
            }
            investigation.setCompletedAt(Instant.now());
            investigationRepository.save(investigation);
        }));
    }

    private Map<String, JsonNode> reusableOutputs(Long parentId) {
        Map<String, JsonNode> outputs = new HashMap<>();
        if (parentId == null) {
            return outputs;
        }
        for (InvestigationStep step : stepRepository.findByInvestigationIdOrderBySequenceNumberAsc(parentId)) {
            if ((step.getStatus() == StepStatus.SUCCEEDED || step.getStatus() == StepStatus.REUSED) && step.getOutputJson() != null) {
                try {
                    outputs.put(step.getAgentName(), objectMapper.readTree(step.getOutputJson()));
                } catch (JsonProcessingException exception) {
                    LOGGER.warn("Ignoring unreadable stored output for stage {}", step.getAgentName());
                }
            }
        }
        return outputs;
    }

    /** D1 = defect report, E# = attached evidence, K# = retrieved knowledge; bounded by the prompt budget. */
    private List<EvidenceItem> buildCatalog(Defect defect, ProjectContextOutput context, RunState state) {
        List<EvidenceItem> candidates = new ArrayList<>();
        SecretRedactor.RedactionResult description = secretRedactor.redact(
                "Title: " + defect.getTitle() + "\nDescription:\n" + defect.getDescription());
        candidates.add(new EvidenceItem("D1", "DEFECT_REPORT", "Defect report", description.text(),
                new SourceRef("DEFECT", null, null, null, null, null, null, null, null, null),
                injectionDetector.isSuspicious(defect.getDescription()), false));
        int index = 1;
        for (DefectEvidence evidence : evidenceRepository.findByDefectIdOrderByCreatedAtAscIdAsc(defect.getId())) {
            int lines = evidence.getContent().split("\n", -1).length;
            candidates.add(new EvidenceItem("E" + index++, evidence.getType().name(), evidence.getLabel(), evidence.getContent(),
                    new SourceRef("DEFECT_EVIDENCE", evidence.getId(), null, null, evidence.getSourceFilename(), null, null,
                            1, lines, null),
                    evidence.isSuspicious(), false));
        }
        if (context != null) {
            candidates.addAll(context.knowledgeItems());
        }

        List<EvidenceItem> catalog = new ArrayList<>();
        int remaining = properties.getMaxPromptEvidenceChars();
        int omitted = 0;
        for (EvidenceItem item : candidates) {
            int allowed = Math.min(properties.getMaxEvidenceItemChars(), remaining);
            if (allowed < MIN_ITEM_BUDGET) {
                omitted++;
                continue;
            }
            boolean truncated = item.truncated() || item.content().length() > allowed;
            String content = item.content().length() > allowed ? item.content().substring(0, allowed) : item.content();
            catalog.add(new EvidenceItem(item.id(), item.kind(), item.label(), content, item.source(), item.suspicious(), truncated));
            remaining -= content.length();
            if (item.suspicious()) {
                state.injectionWarnings.add(item.id() + " (" + item.label() + ") contains instruction-like text; it was treated strictly as data.");
            }
        }
        if (omitted > 0) {
            state.uncertainty.add(omitted + " evidence item(s) were omitted because the evidence size budget was reached.");
        }
        if (catalog.stream().anyMatch(EvidenceItem::truncated)) {
            state.uncertainty.add("Some evidence was truncated to fit the analysis budget; details beyond the excerpt were not analysed.");
        }
        if (catalog.size() == 1) {
            state.uncertainty.add("Only the defect description was available; no logs, stack traces or documents were attached.");
        }
        return catalog;
    }

    private String retrievalQuery(Defect defect) {
        StringBuilder query = new StringBuilder(defect.getTitle()).append('\n');
        String description = defect.getDescription() == null ? "" : defect.getDescription();
        query.append(description, 0, Math.min(description.length(), RETRIEVAL_QUERY_DESCRIPTION_CHARS)).append('\n');
        int signals = 0;
        for (DefectEvidence evidence : evidenceRepository.findByDefectIdOrderByCreatedAtAscIdAsc(defect.getId())) {
            for (String line : evidence.getContent().split("\n")) {
                if (signals >= RETRIEVAL_QUERY_SIGNAL_LINES) {
                    break;
                }
                if (line.contains("Exception") || line.contains("Error") || line.contains("ERROR") || line.contains("FAILED")) {
                    query.append(line.strip()).append('\n');
                    signals++;
                }
            }
        }
        return secretRedactor.redact(query.toString()).text();
    }

    private DefectSnapshot snapshot(Defect defect, ImpactScoreResult baseline) {
        String project = defect.getProjectId() == null ? null
                : projectRepository.findById(defect.getProjectId()).map(item -> item.getName()).orElse(null);
        String component = defect.getComponentId() == null ? null
                : componentRepository.findById(defect.getComponentId()).map(item -> item.getName()).orElse(null);
        return new DefectSnapshot(defect.getId(), defect.getTitle(),
                secretRedactor.redact(defect.getDescription()).text(),
                name(defect.getEnvironment()), defect.getService(), defect.getBusinessFunction(), defect.getAffectedUsers(),
                name(defect.getBusinessCriticality()), name(defect.getCustomerImpact()),
                name(defect.getTechnicalImpact()), name(defect.getStatus()), project, component,
                baseline.totalScore(), baseline.riskLevel(), baseline.priority());
    }

    private InvestigationReport assemble(
            Defect defect,
            ImpactScoreResult baseline,
            ProjectContextOutput context,
            List<EvidenceItem> catalog,
            TriageOutput triage,
            MlSignal mlSignal,
            EvidenceAnalysisOutput findings,
            RootCauseOutput rootCause,
            FixOutput fix,
            RunState state,
            InvestigationStatus status,
            int attempt,
            long durationMs
    ) {
        List<InvestigationReport.HypothesisView> hypotheses = new ArrayList<>();
        if (rootCause != null) {
            for (int index = 0; index < rootCause.hypotheses().size(); index++) {
                Hypothesis hypothesis = rootCause.hypotheses().get(index);
                hypotheses.add(new InvestigationReport.HypothesisView(index + 1, hypothesis.title(), hypothesis.description(),
                        hypothesis.likelihood(), hypothesis.confidence(), InvestigationReport.HYPOTHESIS_UNCONFIRMED,
                        hypothesis.supportingEvidenceIds(), hypothesis.conflictingEvidenceIds(), hypothesis.reasoning()));
            }
        }
        Set<String> missing = new LinkedHashSet<>();
        if (triage != null) {
            missing.addAll(triage.missingInformation());
        }
        if (findings != null) {
            missing.addAll(findings.evidenceGaps());
        }
        if (fix != null) {
            missing.addAll(fix.additionalEvidenceNeeded());
        }
        List<String> uncertainty = new ArrayList<>(state.uncertainty);
        if (rootCause != null) {
            uncertainty.addAll(rootCause.limitations());
        }
        uncertainty.add("All hypotheses are unconfirmed AI suggestions. Confirm with a developer before acting on them.");
        if (context != null && context.note() != null) {
            uncertainty.add(context.note());
        }

        return new InvestigationReport(
                InvestigationReport.SCHEMA_VERSION,
                new InvestigationReport.DefectSection(defect.getId(), defect.getTitle(),
                        triage == null ? defect.getTitle() : triage.summary(),
                        triage == null ? "UNCLASSIFIED" : triage.category(),
                        baseline.riskLevel(), baseline.priority(), name(defect.getEnvironment()), defect.getService(),
                        name(defect.getStatus())),
                new InvestigationReport.ProjectSection(
                        context == null ? defect.getProjectId() : context.projectId(),
                        context == null ? null : context.projectName(),
                        context == null ? defect.getComponentId() : context.componentId(),
                        context == null ? null : context.componentName(),
                        context != null && context.verified(),
                        context == null ? "Project context could not be retrieved" : context.note(),
                        triage == null ? null : triage.affectedComponent()),
                new InvestigationReport.ImpactScoreSection(baseline.totalScore(), baseline.riskLevel(), baseline.priority(),
                        new ImpactScoreBreakdown(baseline.userImpactScore(), baseline.businessImpactScore(),
                                baseline.customerImpactScore(), baseline.technicalImpactScore()),
                        "ImpactScoringService (deterministic)"),
                triage == null ? null : new InvestigationReport.TriageSection(triage.category(), triage.summary(),
                        triage.affectedComponent(), triage.technicalSeverityAssessment(), triage.keySignals(),
                        triage.missingInformation()),
                mlSignal,
                catalog,
                findings == null ? List.of() : findings.findings(),
                hypotheses,
                fix == null ? null : new InvestigationReport.FixSection(fix.fixSteps(), fix.risks(), fix.verificationTests(),
                        fix.regressionTests(), fix.additionalEvidenceNeeded()),
                new InvestigationReport.LimitationsSection(List.copyOf(missing), uncertainty, List.copyOf(state.warnings),
                        List.copyOf(state.injectionWarnings), List.copyOf(state.failedStages),
                        context == null ? null : context.retrievalMode()),
                new InvestigationReport.ExecutionSection(status.name(), invoker.engine(), invoker.model(), attempt, durationMs,
                        Instant.now())
        );
    }

    private String summarize(Object output) {
        if (output instanceof ProjectContextOutput context) {
            return context.note();
        }
        if (output instanceof TriageOutput triage) {
            return "Category " + triage.category() + "; " + triage.missingInformation().size() + " missing-information item(s)";
        }
        if (output instanceof EvidenceAnalysisOutput findings) {
            return findings.findings().size() + " finding(s), " + findings.evidenceGaps().size() + " evidence gap(s)";
        }
        if (output instanceof RootCauseOutput rootCause) {
            return rootCause.hypotheses().size() + " ranked hypothesis(es); top: " + rootCause.hypotheses().get(0).title();
        }
        if (output instanceof FixOutput fix) {
            return fix.fixSteps().size() + " fix step(s), " + fix.verificationTests().size() + " verification and "
                    + fix.regressionTests().size() + " regression test(s)";
        }
        return output == null ? "" : output.toString();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new AgentException(AgentException.Category.INTERNAL, "Investigation data could not be serialised");
        }
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    /** Mutable bookkeeping for one run. */
    /** Shared by the stages of one run; Triage and Evidence Analysis write to it concurrently. */
    private static final class RunState {
        private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
        private final List<String> injectionWarnings = Collections.synchronizedList(new ArrayList<>());
        private final List<String> failedStages = Collections.synchronizedList(new ArrayList<>());
        private final List<String> uncertainty = Collections.synchronizedList(new ArrayList<>());
        private final List<AgentException> failures = Collections.synchronizedList(new ArrayList<>());
        private volatile boolean reuseChain = true;
        private volatile String stopModelStages;

        synchronized void recordFailure(String stage, AgentException exception) {
            failures.add(exception);
            failedStages.add(stage + ": " + exception.category() + " - " + exception.getMessage());
        }

        String primaryErrorCategory() {
            return failures.isEmpty() ? "NO_HYPOTHESES" : failures.get(failures.size() - 1).category().name();
        }

        String primaryErrorMessage() {
            return failures.isEmpty() ? "Root cause analysis produced no result" : failures.get(failures.size() - 1).getMessage();
        }
    }

    static Optional<String> displayName(String stage) {
        return Optional.ofNullable(DISPLAY_NAMES.get(stage));
    }
}

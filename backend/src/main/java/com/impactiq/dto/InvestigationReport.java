package com.impactiq.dto;

import com.impactiq.agent.AgentContracts.EvidenceFinding;
import com.impactiq.agent.AgentContracts.EvidenceItem;
import com.impactiq.agent.AgentContracts.FixStep;
import com.impactiq.agent.AgentContracts.MlSignal;
import com.impactiq.agent.AgentContracts.TestSuggestion;

import java.time.Instant;
import java.util.List;

/**
 * Persisted investigation report (stored as JSON). Severity and priority always come from the deterministic
 * ImpactScoringService; every hypothesis status is UNCONFIRMED until a developer records feedback.
 */
public record InvestigationReport(
        int schemaVersion,
        DefectSection defect,
        ProjectSection project,
        ImpactScoreSection impactScore,
        TriageSection triage,
        MlSignal mlSignal,
        List<EvidenceItem> evidence,
        List<EvidenceFinding> findings,
        List<HypothesisView> hypotheses,
        FixSection fix,
        LimitationsSection limitations,
        ExecutionSection execution
) {

    public static final int SCHEMA_VERSION = 1;
    public static final String HYPOTHESIS_UNCONFIRMED = "UNCONFIRMED";

    public record DefectSection(
            Long id,
            String title,
            String summary,
            String category,
            String severity,
            String priority,
            String environment,
            String service,
            String status
    ) {
    }

    public record ProjectSection(
            Long projectId,
            String projectName,
            Long componentId,
            String componentName,
            boolean verified,
            String note,
            String aiSuggestedComponent
    ) {
    }

    public record ImpactScoreSection(
            int score,
            String riskLevel,
            String priority,
            ImpactScoreBreakdown breakdown,
            String source
    ) {
    }

    public record TriageSection(
            String category,
            String summary,
            String affectedComponent,
            String advisoryTechnicalSeverity,
            List<String> keySignals,
            List<String> missingInformation
    ) {
    }

    public record HypothesisView(
            int rank,
            String title,
            String description,
            String likelihood,
            int confidence,
            String status,
            List<String> supportingEvidenceIds,
            List<String> conflictingEvidenceIds,
            String reasoning
    ) {
    }

    public record FixSection(
            List<FixStep> steps,
            List<String> risks,
            List<TestSuggestion> verificationTests,
            List<TestSuggestion> regressionTests,
            List<String> additionalEvidenceNeeded
    ) {
    }

    public record LimitationsSection(
            List<String> missingEvidence,
            List<String> uncertainty,
            List<String> validationWarnings,
            List<String> promptInjectionWarnings,
            List<String> failedStages,
            String retrievalMode
    ) {
    }

    public record ExecutionSection(
            String status,
            String engine,
            String model,
            int attempt,
            long durationMs,
            Instant generatedAt
    ) {
    }
}

package com.impactiq.agent;

import java.util.List;

/**
 * Typed input/output contracts for the specialist agents. Model output is parsed into these records and
 * then validated server-side; nothing reaches the report without passing validation.
 */
public final class AgentContracts {

    public static final List<String> CATEGORIES = List.of(
            "FUNCTIONAL", "PERFORMANCE", "SECURITY", "DATA_INTEGRITY", "INTEGRATION",
            "CONFIGURATION", "INFRASTRUCTURE", "UI_UX", "OTHER");
    public static final List<String> LEVELS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    public static final List<String> LIKELIHOODS = List.of("LOW", "MEDIUM", "HIGH");
    public static final List<String> TEST_TYPES = List.of("UNIT", "INTEGRATION", "END_TO_END", "MANUAL", "PERFORMANCE", "SECURITY");

    private AgentContracts() {
    }

    /** Evidence presented to agents. IDs: D1 = defect report, E# = attached evidence, K# = project knowledge. */
    public record EvidenceItem(
            String id,
            String kind,
            String label,
            String content,
            SourceRef source,
            boolean suspicious,
            boolean truncated
    ) {
    }

    public record SourceRef(
            String type,
            Long evidenceId,
            Long documentId,
            Integer versionNumber,
            String filename,
            Long chunkId,
            Integer pageNumber,
            Integer startLine,
            Integer endLine,
            Double retrievalScore
    ) {
    }

    public record ProjectContextOutput(
            Long projectId,
            String projectName,
            Long componentId,
            String componentName,
            boolean verified,
            String note,
            String retrievalMode,
            String query,
            List<EvidenceItem> knowledgeItems
    ) {
    }

    public record TriageOutput(
            String category,
            String summary,
            String affectedComponent,
            String technicalSeverityAssessment,
            List<String> keySignals,
            List<String> missingInformation
    ) {
    }

    /** Top-k prediction of the trained triage model (advisory; never changes the deterministic priority). */
    public record MlSignal(
            List<LabelScore> severity,
            List<LabelScore> component,
            String source
    ) {
    }

    public record LabelScore(
            String label,
            double probability
    ) {
    }

    public record EvidenceFinding(
            String evidenceId,
            String observation,
            String significance,
            String location
    ) {
    }

    public record EvidenceAnalysisOutput(
            List<EvidenceFinding> findings,
            List<String> evidenceGaps
    ) {
    }

    public record Hypothesis(
            String title,
            String description,
            String likelihood,
            Integer confidence,
            List<String> supportingEvidenceIds,
            List<String> conflictingEvidenceIds,
            String reasoning
    ) {
    }

    public record RootCauseOutput(
            List<Hypothesis> hypotheses,
            List<String> limitations
    ) {
    }

    public record FixStep(
            String action,
            String rationale,
            Integer hypothesisRank,
            String risk
    ) {
    }

    public record TestSuggestion(
            String name,
            String type,
            String steps,
            String expectedResult
    ) {
    }

    public record FixOutput(
            List<FixStep> fixSteps,
            List<String> risks,
            List<TestSuggestion> verificationTests,
            List<TestSuggestion> regressionTests,
            List<String> additionalEvidenceNeeded
    ) {
    }
}

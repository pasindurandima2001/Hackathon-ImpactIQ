package com.impactiq.agent;

import com.impactiq.agent.AgentContracts.EvidenceAnalysisOutput;
import com.impactiq.agent.AgentContracts.EvidenceItem;
import com.impactiq.agent.AgentContracts.Hypothesis;
import com.impactiq.agent.AgentContracts.RootCauseOutput;
import com.impactiq.agent.AgentContracts.TriageOutput;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Produces ranked, evidence-cited root-cause hypotheses. Hypotheses are never marked as confirmed. */
@Component
public class RootCauseAnalysisAgent implements InvestigationAgent<RootCauseAnalysisAgent.Input, RootCauseOutput> {

    private static final int MAX_HYPOTHESES = 5;

    private static final String SYSTEM = PromptSupport.system(
            "You are the Root Cause Analysis Agent of ImpactIQ, a software defect investigation assistant.",
            """
            Propose up to five candidate root causes, ordered from most to least likely. For each hypothesis give a short
            title, a precise technical description, a likelihood, an uncalibrated confidence from 0 to 100, the evidence IDs
            that support it, the evidence IDs that conflict with it or weaken it, and reasoning that connects the evidence to
            the cause. Every hypothesis must cite at least one supporting evidence ID; when support is weak, lower the
            likelihood and confidence and explain why. Prefer specific, testable causes over generic ones. List the
            limitations of this analysis, including missing evidence and assumptions.
            """);

    private final StructuredAgentInvoker invoker;

    public RootCauseAnalysisAgent(StructuredAgentInvoker invoker) {
        this.invoker = invoker;
    }

    public record Input(DefectSnapshot defect, TriageOutput triage, EvidenceAnalysisOutput findings, List<EvidenceItem> evidence) {
    }

    @Override
    public String name() {
        return "root_cause_analysis_agent";
    }

    @Override
    public String displayName() {
        return "Root Cause Analysis Agent";
    }

    @Override
    public AgentOutcome<RootCauseOutput> run(Input input, Instant deadline) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("defect", input.defect());
        if (input.triage() != null) {
            payload.put("triage", input.triage());
        }
        if (input.findings() != null) {
            payload.put("evidenceFindings", input.findings());
        }
        payload.put("evidence", input.evidence());
        String prompt = "Generate ranked root-cause hypotheses for this defect.\n" + PromptSupport.untrusted(payload);
        Set<String> known = input.evidence().stream().map(EvidenceItem::id).collect(Collectors.toSet());
        List<String> unverified = new ArrayList<>();
        StructuredAgentInvoker.Result<RootCauseOutput> result = invoker.invoke(name(),
                "Generates ranked root-cause hypotheses with supporting and conflicting evidence", SYSTEM, prompt,
                AgentSchemas.ROOT_CAUSE, RootCauseOutput.class, output -> validate(output, known, unverified), deadline);
        return new AgentOutcome<>(result.output(), result.attempts(), EvidenceAnalysisAgent.citationWarnings(unverified));
    }

    /**
     * Drops hypotheses without any verifiable supporting evidence, removes unknown citations, and re-sorts by
     * likelihood then confidence so the server, not the model, decides the final ranking order.
     */
    static RootCauseOutput validate(RootCauseOutput output, Set<String> known, List<String> unverified) {
        List<Hypothesis> hypotheses = new ArrayList<>();
        List<String> limitations = new ArrayList<>(OutputSanitizer.texts(output.limitations(), 10, 300));
        int unsupported = 0;
        if (output.hypotheses() != null) {
            for (Hypothesis hypothesis : output.hypotheses()) {
                if (hypothesis == null) {
                    continue;
                }
                String title = OutputSanitizer.text(hypothesis.title(), 300);
                if (title.isEmpty()) {
                    continue;
                }
                List<String> supporting = OutputSanitizer.citations(hypothesis.supportingEvidenceIds(), known, unverified);
                List<String> conflicting = OutputSanitizer.citations(hypothesis.conflictingEvidenceIds(), known, unverified);
                conflicting = conflicting.stream().filter(id -> !supporting.contains(id)).toList();
                if (supporting.isEmpty()) {
                    unsupported++;
                    continue;
                }
                hypotheses.add(new Hypothesis(title,
                        OutputSanitizer.text(hypothesis.description(), 1500),
                        OutputSanitizer.oneOf(hypothesis.likelihood(), AgentContracts.LIKELIHOODS, "LOW"),
                        OutputSanitizer.clamp(hypothesis.confidence(), 0, 100, 0),
                        supporting, conflicting,
                        OutputSanitizer.text(hypothesis.reasoning(), 4000)));
            }
        }
        if (unsupported > 0) {
            limitations.add(unsupported + " hypothesis(es) were discarded because they cited no verifiable evidence.");
        }
        if (hypotheses.isEmpty()) {
            throw new AgentException(AgentException.Category.INVALID_OUTPUT, "No evidence-backed hypotheses were produced");
        }
        List<Hypothesis> ranked = hypotheses.stream()
                .sorted(Comparator.comparingInt((Hypothesis item) -> AgentContracts.LIKELIHOODS.indexOf(item.likelihood())).reversed()
                        .thenComparing(Comparator.comparingInt(Hypothesis::confidence).reversed()))
                .limit(MAX_HYPOTHESES)
                .toList();
        return new RootCauseOutput(ranked, limitations);
    }
}

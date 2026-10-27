package com.impactiq.agent;

import com.impactiq.agent.AgentContracts.EvidenceAnalysisOutput;
import com.impactiq.agent.AgentContracts.EvidenceFinding;
import com.impactiq.agent.AgentContracts.EvidenceItem;
import com.impactiq.agent.AgentContracts.TriageOutput;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Reads logs, stack traces, code, test results and retrieved documents, producing cited observations. */
@Component
public class EvidenceAnalysisAgent implements InvestigationAgent<EvidenceAnalysisAgent.Input, EvidenceAnalysisOutput> {

    private static final String SYSTEM = PromptSupport.system(
            "You are the Evidence Analysis Agent of ImpactIQ, a software defect investigation assistant.",
            """
            Analyse every evidence item: logs, stack traces, code snippets, configuration, test results, the defect report
            and project knowledge passages. Report concrete observations that matter for finding the root cause, such as
            exception types, failing frames, error codes, timestamps, configuration values, failing assertions and
            mismatches between documentation and observed behaviour. Each finding must reference exactly one evidence ID
            and, when possible, a precise location such as a line number, frame, page or field.
            Rate the significance of each finding. List gaps where evidence that would settle the question is missing.
            """);

    private final StructuredAgentInvoker invoker;

    public EvidenceAnalysisAgent(StructuredAgentInvoker invoker) {
        this.invoker = invoker;
    }

    public record Input(DefectSnapshot defect, TriageOutput triage, List<EvidenceItem> evidence) {
    }

    @Override
    public String name() {
        return "evidence_analysis_agent";
    }

    @Override
    public String displayName() {
        return "Evidence Analysis Agent";
    }

    @Override
    public AgentOutcome<EvidenceAnalysisOutput> run(Input input, Instant deadline) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("defect", input.defect());
        if (input.triage() != null) {
            payload.put("triage", input.triage());
        }
        payload.put("evidence", input.evidence());
        String prompt = "Analyse the evidence for this defect.\n" + PromptSupport.untrusted(payload);
        Set<String> known = input.evidence().stream().map(EvidenceItem::id).collect(Collectors.toSet());
        List<String> unverified = new ArrayList<>();
        StructuredAgentInvoker.Result<EvidenceAnalysisOutput> result = invoker.invoke(name(),
                "Analyses defect evidence and cites sources", SYSTEM, prompt, AgentSchemas.EVIDENCE_ANALYSIS,
                EvidenceAnalysisOutput.class, output -> validate(output, known, unverified), deadline);
        return new AgentOutcome<>(result.output(), result.attempts(), citationWarnings(unverified));
    }

    static EvidenceAnalysisOutput validate(EvidenceAnalysisOutput output, Set<String> known, List<String> unverified) {
        List<EvidenceFinding> findings = new ArrayList<>();
        if (output.findings() != null) {
            for (EvidenceFinding finding : output.findings()) {
                if (finding == null || finding.evidenceId() == null) {
                    continue;
                }
                String id = finding.evidenceId().strip().toUpperCase(Locale.ROOT);
                if (!known.contains(id)) {
                    if (!unverified.contains(id)) {
                        unverified.add(id.length() > 12 ? id.substring(0, 12) : id);
                    }
                    continue;
                }
                String observation = OutputSanitizer.text(finding.observation(), 1500);
                if (observation.isEmpty()) {
                    continue;
                }
                findings.add(new EvidenceFinding(id, observation,
                        OutputSanitizer.oneOf(finding.significance(), AgentContracts.LIKELIHOODS, "MEDIUM"),
                        OutputSanitizer.text(finding.location(), 300)));
                if (findings.size() == 15) {
                    break;
                }
            }
        }
        return new EvidenceAnalysisOutput(findings, OutputSanitizer.texts(output.evidenceGaps(), 10, 300));
    }

    static List<String> citationWarnings(List<String> unverified) {
        return unverified.isEmpty() ? List.of()
                : List.of("Removed references to unknown evidence IDs: " + String.join(", ", unverified));
    }
}

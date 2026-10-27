package com.impactiq.agent;

import com.impactiq.agent.AgentContracts.EvidenceItem;
import com.impactiq.agent.AgentContracts.MlSignal;
import com.impactiq.agent.AgentContracts.TriageOutput;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Classifies the defect and lists missing information. Its severity view is advisory only. */
@Component
public class DefectTriageAgent implements InvestigationAgent<DefectTriageAgent.Input, TriageOutput> {

    private static final int EXCERPT_CHARS = 1500;

    private static final String SYSTEM = PromptSupport.system(
            "You are the Defect Triage Agent of ImpactIQ, a software defect investigation assistant.",
            """
            Classify the defect into one category, write a concise neutral summary of the reported problem,
            name the most likely affected component using only names present in the data (or state that it is unknown),
            give an advisory technical severity view, list the key signals you relied on,
            and list the information a developer would still need to investigate it (missing logs, versions, steps to reproduce, and so on).
            The advisory severity is only your technical opinion and never replaces the deterministic priority.
            If an mlSignal is present it comes from a statistical text classifier trained on public bug reports: treat it as one
            weak signal, weigh it against the evidence, and do not copy its component names unless they appear in the data.
            """);

    private final StructuredAgentInvoker invoker;

    public DefectTriageAgent(StructuredAgentInvoker invoker) {
        this.invoker = invoker;
    }

    public record Input(DefectSnapshot defect, List<EvidenceItem> evidence, MlSignal mlSignal) {
    }

    @Override
    public String name() {
        return "defect_triage_agent";
    }

    @Override
    public String displayName() {
        return "Defect Triage Agent";
    }

    @Override
    public AgentOutcome<TriageOutput> run(Input input, Instant deadline) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("defect", input.defect());
        payload.put("evidence", input.evidence().stream().map(item -> Map.of(
                "id", item.id(),
                "kind", item.kind(),
                "label", item.label(),
                "suspicious", item.suspicious(),
                "excerpt", item.content().length() > EXCERPT_CHARS ? item.content().substring(0, EXCERPT_CHARS) : item.content()
        )).toList());
        if (input.mlSignal() != null) {
            payload.put("mlSignal", input.mlSignal());
        }
        String prompt = "Triage this defect.\n" + PromptSupport.untrusted(payload);
        StructuredAgentInvoker.Result<TriageOutput> result = invoker.invoke(name(), "Classifies defects and finds missing information",
                SYSTEM, prompt, AgentSchemas.TRIAGE, TriageOutput.class, DefectTriageAgent::validate, deadline);
        return new AgentOutcome<>(result.output(), result.attempts(), List.of());
    }

    static TriageOutput validate(TriageOutput output) {
        return new TriageOutput(
                OutputSanitizer.oneOf(output.category(), AgentContracts.CATEGORIES, "OTHER"),
                OutputSanitizer.requiredText(output.summary(), 1500, "summary"),
                OutputSanitizer.text(output.affectedComponent(), 300),
                OutputSanitizer.oneOf(output.technicalSeverityAssessment(), AgentContracts.LEVELS, "MEDIUM"),
                OutputSanitizer.texts(output.keySignals(), 10, 300),
                OutputSanitizer.texts(output.missingInformation(), 10, 300));
    }
}

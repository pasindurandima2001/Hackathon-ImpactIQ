package com.impactiq.agent;

import com.impactiq.agent.AgentContracts.EvidenceItem;
import com.impactiq.agent.AgentContracts.FixOutput;
import com.impactiq.agent.AgentContracts.FixStep;
import com.impactiq.agent.AgentContracts.RootCauseOutput;
import com.impactiq.agent.AgentContracts.TestSuggestion;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Proposes fix steps, risks, verification and regression tests for the ranked hypotheses. */
@Component
public class FixRecommendationAgent implements InvestigationAgent<FixRecommendationAgent.Input, FixOutput> {

    private static final String SYSTEM = PromptSupport.system(
            "You are the Fix Recommendation Agent of ImpactIQ, a software defect investigation assistant.",
            """
            Using the ranked root-cause hypotheses (rank 1 is the most likely) and the evidence, propose concrete, ordered
            fix steps a developer can act on. Link each step to the hypothesis rank it addresses and state its main risk.
            List overall risks and side effects of the change. Propose verification tests that prove the fix works and
            regression tests that protect related behaviour, each with a type, steps and an expected result. Because the
            hypotheses are unconfirmed, include steps or tests that first confirm the cause before changing code when the
            evidence is weak. List additional evidence that would increase confidence. Do not propose running commands
            against production, deleting data, or deploying; describe changes for a developer to review instead.
            """);

    private final StructuredAgentInvoker invoker;

    public FixRecommendationAgent(StructuredAgentInvoker invoker) {
        this.invoker = invoker;
    }

    public record Input(DefectSnapshot defect, RootCauseOutput rootCause, List<EvidenceItem> evidence) {
    }

    @Override
    public String name() {
        return "fix_recommendation_agent";
    }

    @Override
    public String displayName() {
        return "Fix Recommendation Agent";
    }

    @Override
    public AgentOutcome<FixOutput> run(Input input, Instant deadline) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("defect", input.defect());
        List<Map<String, Object>> ranked = new ArrayList<>();
        for (int index = 0; index < input.rootCause().hypotheses().size(); index++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rank", index + 1);
            entry.put("hypothesis", input.rootCause().hypotheses().get(index));
            ranked.add(entry);
        }
        payload.put("rankedHypotheses", ranked);
        payload.put("evidence", input.evidence());
        String prompt = "Recommend a fix and tests for this defect.\n" + PromptSupport.untrusted(payload);
        int hypothesisCount = input.rootCause().hypotheses().size();
        StructuredAgentInvoker.Result<FixOutput> result = invoker.invoke(name(),
                "Recommends fixes, risks, verification and regression tests", SYSTEM, prompt, AgentSchemas.FIX,
                FixOutput.class, output -> validate(output, hypothesisCount), deadline);
        return new AgentOutcome<>(result.output(), result.attempts(), List.of());
    }

    static FixOutput validate(FixOutput output, int hypothesisCount) {
        List<FixStep> steps = new ArrayList<>();
        if (output.fixSteps() != null) {
            for (FixStep step : output.fixSteps()) {
                if (step == null) {
                    continue;
                }
                String action = OutputSanitizer.text(step.action(), 1500);
                if (action.isEmpty()) {
                    continue;
                }
                Integer rank = step.hypothesisRank() != null && step.hypothesisRank() >= 1 && step.hypothesisRank() <= hypothesisCount
                        ? step.hypothesisRank() : null;
                steps.add(new FixStep(action, OutputSanitizer.text(step.rationale(), 1500), rank,
                        OutputSanitizer.text(step.risk(), 300)));
                if (steps.size() == 10) {
                    break;
                }
            }
        }
        if (steps.isEmpty()) {
            throw new AgentException(AgentException.Category.INVALID_OUTPUT, "No actionable fix steps were produced");
        }
        return new FixOutput(steps,
                OutputSanitizer.texts(output.risks(), 10, 300),
                tests(output.verificationTests()),
                tests(output.regressionTests()),
                OutputSanitizer.texts(output.additionalEvidenceNeeded(), 10, 300));
    }

    private static List<TestSuggestion> tests(List<TestSuggestion> tests) {
        if (tests == null) {
            return List.of();
        }
        return tests.stream()
                .filter(test -> test != null && !OutputSanitizer.text(test.name(), 300).isEmpty())
                .limit(8)
                .map(test -> new TestSuggestion(
                        OutputSanitizer.text(test.name(), 300),
                        OutputSanitizer.oneOf(test.type(), AgentContracts.TEST_TYPES, "MANUAL"),
                        OutputSanitizer.text(test.steps(), 1500),
                        OutputSanitizer.text(test.expectedResult(), 300)))
                .toList();
    }
}

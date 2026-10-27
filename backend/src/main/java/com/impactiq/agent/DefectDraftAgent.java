package com.impactiq.agent;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Intake assistant that turns a short description, a pasted error or an uploaded file into an editable defect draft.
 * Its output is only a suggestion shown to the developer; nothing is saved without explicit confirmation.
 */
@Component
public class DefectDraftAgent {

    public static final String NAME = "defect_draft_agent";
    private static final List<String> ENVIRONMENTS = List.of("DEVELOPMENT", "TEST", "STAGING", "PRODUCTION", "UNKNOWN");
    private static final List<String> SIGNALS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL", "UNKNOWN");

    private static final String SYSTEM = PromptSupport.system(
            "You are the Defect Intake Assistant of ImpactIQ, a software defect investigation assistant.",
            """
            Draft a defect record from the supplied input. Write a specific title of at most 120 characters and a clear
            description with observed behaviour, expected behaviour, and any steps, error messages or stack frames present
            in the input. Suggest the environment, service and business function only when the input names or clearly
            implies them; otherwise use UNKNOWN or an empty string. Suggest impact signals only when the input supports
            them, otherwise use UNKNOWN. Use -1 for affectedUsers when the number is not stated. Never invent customer
            numbers, services or business facts. Classify the defect, list information that is still missing, and explain
            your suggestions briefly in the rationale.
            """);

    private static final Map<String, Object> SCHEMA = AgentSchemas.object(new LinkedHashMap<>(Map.ofEntries(
            Map.entry("title", AgentSchemas.string(120)),
            Map.entry("description", AgentSchemas.string(6000)),
            Map.entry("environment", AgentSchemas.enumString(ENVIRONMENTS)),
            Map.entry("service", AgentSchemas.string(120)),
            Map.entry("businessFunction", AgentSchemas.string(120)),
            Map.entry("affectedUsers", AgentSchemas.integer(-1, 100_000_000)),
            Map.entry("businessCriticality", AgentSchemas.enumString(SIGNALS)),
            Map.entry("customerImpact", AgentSchemas.enumString(SIGNALS)),
            Map.entry("technicalImpact", AgentSchemas.enumString(SIGNALS)),
            Map.entry("category", AgentSchemas.enumString(AgentContracts.CATEGORIES)),
            Map.entry("missingInformation", AgentSchemas.stringArray(8, 200)),
            Map.entry("rationale", AgentSchemas.string(1500))
    )), List.of("title", "description", "environment", "service", "businessFunction", "affectedUsers",
            "businessCriticality", "customerImpact", "technicalImpact", "category", "missingInformation", "rationale"));

    private final StructuredAgentInvoker invoker;

    public DefectDraftAgent(StructuredAgentInvoker invoker) {
        this.invoker = invoker;
    }

    public record DraftOutput(
            String title,
            String description,
            String environment,
            String service,
            String businessFunction,
            Integer affectedUsers,
            String businessCriticality,
            String customerImpact,
            String technicalImpact,
            String category,
            List<String> missingInformation,
            String rationale
    ) {
    }

    public String engine() {
        return invoker.engine();
    }

    public DraftOutput draft(String mode, String input, String projectContext, Instant deadline) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mode", mode);
        if (projectContext != null) {
            payload.put("project", projectContext);
        }
        payload.put("input", input);
        String prompt = "Draft a defect from this input.\n" + PromptSupport.untrusted(payload);
        return invoker.invoke(NAME, "Drafts editable defect records from raw input", SYSTEM, prompt, SCHEMA,
                DraftOutput.class, DefectDraftAgent::validate, deadline).output();
    }

    static DraftOutput validate(DraftOutput output) {
        return new DraftOutput(
                OutputSanitizer.requiredText(output.title(), 120, "title"),
                OutputSanitizer.requiredText(output.description(), 6000, "description"),
                OutputSanitizer.oneOf(output.environment(), ENVIRONMENTS, "UNKNOWN"),
                OutputSanitizer.text(output.service(), 120),
                OutputSanitizer.text(output.businessFunction(), 120),
                output.affectedUsers() == null || output.affectedUsers() < 0 ? null : output.affectedUsers(),
                OutputSanitizer.oneOf(output.businessCriticality(), SIGNALS, "UNKNOWN"),
                OutputSanitizer.oneOf(output.customerImpact(), SIGNALS, "UNKNOWN"),
                OutputSanitizer.oneOf(output.technicalImpact(), SIGNALS, "UNKNOWN"),
                OutputSanitizer.oneOf(output.category(), AgentContracts.CATEGORIES, "OTHER"),
                OutputSanitizer.texts(output.missingInformation(), 8, 200),
                OutputSanitizer.text(output.rationale(), 1500));
    }
}

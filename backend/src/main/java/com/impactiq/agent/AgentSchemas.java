package com.impactiq.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON response schemas for each agent, in the same map style used by ImpactAnalysisService. */
public final class AgentSchemas {

    private static final int SHORT = 300;
    private static final int MEDIUM = 1500;
    private static final int LONG = 4000;

    private AgentSchemas() {
    }

    public static final Map<String, Object> TRIAGE = object(Map.of(
            "category", enumString(AgentContracts.CATEGORIES),
            "summary", string(MEDIUM),
            "affectedComponent", string(SHORT),
            "technicalSeverityAssessment", enumString(AgentContracts.LEVELS),
            "keySignals", stringArray(10, SHORT),
            "missingInformation", stringArray(10, SHORT)
    ), List.of("category", "summary", "affectedComponent", "technicalSeverityAssessment", "keySignals", "missingInformation"));

    public static final Map<String, Object> EVIDENCE_ANALYSIS = object(Map.of(
            "findings", array(object(Map.of(
                    "evidenceId", string(8),
                    "observation", string(MEDIUM),
                    "significance", enumString(AgentContracts.LIKELIHOODS),
                    "location", string(SHORT)
            ), List.of("evidenceId", "observation", "significance", "location")), 15),
            "evidenceGaps", stringArray(10, SHORT)
    ), List.of("findings", "evidenceGaps"));

    public static final Map<String, Object> ROOT_CAUSE = object(Map.of(
            "hypotheses", array(object(Map.of(
                    "title", string(SHORT),
                    "description", string(MEDIUM),
                    "likelihood", enumString(AgentContracts.LIKELIHOODS),
                    "confidence", integer(0, 100),
                    "supportingEvidenceIds", stringArray(10, 8),
                    "conflictingEvidenceIds", stringArray(10, 8),
                    "reasoning", string(LONG)
            ), List.of("title", "description", "likelihood", "confidence", "supportingEvidenceIds",
                    "conflictingEvidenceIds", "reasoning")), 5),
            "limitations", stringArray(10, SHORT)
    ), List.of("hypotheses", "limitations"));

    private static final Map<String, Object> TEST = object(Map.of(
            "name", string(SHORT),
            "type", enumString(AgentContracts.TEST_TYPES),
            "steps", string(MEDIUM),
            "expectedResult", string(SHORT)
    ), List.of("name", "type", "steps", "expectedResult"));

    public static final Map<String, Object> FIX = object(Map.of(
            "fixSteps", array(object(Map.of(
                    "action", string(MEDIUM),
                    "rationale", string(MEDIUM),
                    "hypothesisRank", integer(1, 5),
                    "risk", string(SHORT)
            ), List.of("action", "rationale", "hypothesisRank", "risk")), 10),
            "risks", stringArray(10, SHORT),
            "verificationTests", array(TEST, 8),
            "regressionTests", array(TEST, 8),
            "additionalEvidenceNeeded", stringArray(10, SHORT)
    ), List.of("fixSteps", "risks", "verificationTests", "regressionTests", "additionalEvidenceNeeded"));

    static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    static Map<String, Object> string(int maxLength) {
        return Map.of("type", "string", "maxLength", maxLength);
    }

    static Map<String, Object> enumString(List<String> values) {
        return Map.of("type", "string", "enum", values);
    }

    static Map<String, Object> integer(int minimum, int maximum) {
        return Map.of("type", "integer", "minimum", minimum, "maximum", maximum);
    }

    static Map<String, Object> array(Map<String, Object> items, int maxItems) {
        return Map.of("type", "array", "maxItems", maxItems, "items", items);
    }

    static Map<String, Object> stringArray(int maxItems, int maxLength) {
        return array(string(maxLength), maxItems);
    }
}

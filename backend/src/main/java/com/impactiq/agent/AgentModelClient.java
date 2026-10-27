package com.impactiq.agent;

import java.util.Map;

/**
 * Runtime that executes one specialist agent turn and returns its raw JSON output.
 * Implementations: {@link AdkAgentModelClient} (Google ADK LlmAgent) and {@link GeminiDirectModelClient}.
 * Agents get no tools: they cannot run shell commands, query databases, or reach production systems.
 */
public interface AgentModelClient {

    String engine();

    String model();

    String generateJson(AgentCall call);

    record AgentCall(
            String agentName,
            String description,
            String systemInstruction,
            String userPrompt,
            Map<String, Object> responseSchema,
            int maxOutputTokens,
            long timeoutMs
    ) {
    }
}

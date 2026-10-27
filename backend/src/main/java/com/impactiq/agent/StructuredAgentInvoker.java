package com.impactiq.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactiq.config.InvestigationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Calls the agent runtime with bounded retries, a per-call timeout derived from the run deadline, JSON parsing
 * and server-side validation. Only transient failures and invalid output are retried.
 */
@Component
public class StructuredAgentInvoker {

    private static final Logger LOGGER = LoggerFactory.getLogger(StructuredAgentInvoker.class);
    private static final long MIN_CALL_BUDGET_MS = 5_000;
    private static final long MAX_CALL_TIMEOUT_MS = 90_000;

    private final AgentModelClient modelClient;
    private final InvestigationProperties properties;
    private final ObjectMapper objectMapper;

    public StructuredAgentInvoker(AgentModelClient modelClient, InvestigationProperties properties, ObjectMapper objectMapper) {
        this.modelClient = modelClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String engine() {
        return modelClient.engine();
    }

    public String model() {
        return modelClient.model();
    }

    /**
     * @param validator validates and normalises the parsed output; throws {@link AgentException} with
     *                  {@code INVALID_OUTPUT} when the output is unusable.
     */
    public <T> Result<T> invoke(
            String agentName,
            String description,
            String systemInstruction,
            String userPrompt,
            Map<String, Object> schema,
            Class<T> type,
            UnaryOperator<T> validator,
            Instant deadline
    ) {
        int maxAttempts = Math.max(1, properties.getAgentMaxAttempts());
        AgentException last = null;
        int attemptsMade = 0;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long remaining = deadline.toEpochMilli() - System.currentTimeMillis();
            if (remaining < MIN_CALL_BUDGET_MS) {
                throw new AgentException(AgentException.Category.RUN_TIMEOUT, "Investigation time budget was exhausted");
            }
            attemptsMade = attempt;
            try {
                String raw = modelClient.generateJson(new AgentModelClient.AgentCall(agentName, description,
                        systemInstruction, userPrompt, schema, properties.getMaxOutputTokens(),
                        Math.min(remaining, MAX_CALL_TIMEOUT_MS)));
                T parsed = parse(raw, type);
                return new Result<>(validator.apply(parsed), attempt);
            } catch (AgentException exception) {
                last = exception;
                LOGGER.warn("Agent {} attempt {}/{} failed: category={}", agentName, attempt, maxAttempts, exception.category());
                if (!exception.category().retryable() || attempt == maxAttempts) {
                    break;
                }
                sleep(properties.getRetryBackoffMs() * attempt);
            }
        }
        throw new AgentException(last.category(), last.getMessage(), attemptsMade);
    }

    private <T> T parse(String raw, Class<T> type) {
        try {
            JsonNode json = objectMapper.readTree(stripFences(raw));
            if (json == null || !json.isObject()) {
                throw new AgentException(AgentException.Category.INVALID_OUTPUT, "Agent output was not a JSON object");
            }
            return objectMapper.treeToValue(json, type);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new AgentException(AgentException.Category.INVALID_OUTPUT, "Agent output did not match its contract");
        }
    }

    /** Some runtimes wrap JSON in markdown fences; remove them before parsing. */
    static String stripFences(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int closing = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && closing > firstNewline) {
                return trimmed.substring(firstNewline + 1, closing).trim();
            }
        }
        return trimmed;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(Math.max(0, millis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AgentException(AgentException.Category.INTERNAL, "Investigation was interrupted");
        }
    }

    public record Result<T>(T output, int attempts) {
    }
}

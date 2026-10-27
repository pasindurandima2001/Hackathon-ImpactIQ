package com.impactiq.agent;

import com.google.adk.agents.LlmAgent;
import com.google.adk.events.Event;
import com.google.adk.models.Gemini;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.ThinkingConfig;
import com.impactiq.exception.GeminiServiceException;
import com.impactiq.service.GeminiClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs each specialist as a Google ADK {@link LlmAgent} (google-adk 1.11.0) with a structured output schema,
 * executed by an {@link InMemoryRunner} in a fresh, single-turn session. The ADK Gemini model reuses the
 * application's shared google-genai Client. No tools, sub-agents, or transfers are configured.
 */
public class AdkAgentModelClient implements AgentModelClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdkAgentModelClient.class);
    private static final String APP_NAME = "impactiq";
    private static final String USER_ID = "investigation";
    private static final float TEMPERATURE = 0.2f;

    private final GeminiClientFactory clientFactory;
    /** Upper-cased Gemini thinking level, or null to keep the model default. */
    private final String thinkingLevel;

    public AdkAgentModelClient(GeminiClientFactory clientFactory, String thinkingLevel) {
        this.clientFactory = clientFactory;
        this.thinkingLevel = thinkingLevel == null || thinkingLevel.isBlank() ? null : thinkingLevel.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public String engine() {
        return "ADK";
    }

    @Override
    public String model() {
        return clientFactory.model();
    }

    private GenerateContentConfig generationConfig(AgentCall call) {
        GenerateContentConfig.Builder config = GenerateContentConfig.builder()
                .maxOutputTokens(call.maxOutputTokens())
                .temperature(TEMPERATURE);
        if (thinkingLevel != null) {
            // Lower thinking cuts latency: each specialist gets a narrow, schema-bound task with its evidence inline.
            config.thinkingConfig(ThinkingConfig.builder().thinkingLevel(thinkingLevel).build());
        }
        return config.build();
    }

    @Override
    public String generateJson(AgentCall call) {
        if (!clientFactory.isConfigured()) {
            throw new AgentException(AgentException.Category.MISSING_API_KEY, "Gemini API key is not configured");
        }
        LlmAgent agent = LlmAgent.builder()
                .name(call.agentName())
                .description(call.description())
                .model(new Gemini(clientFactory.model(), clientFactory.client()))
                .instruction(call.systemInstruction())
                .outputSchema(toGenaiSchema(call.responseSchema()))
                .generateContentConfig(generationConfig(call))
                .disallowTransferToParent(true)
                .disallowTransferToPeers(true)
                .build();
        InMemoryRunner runner = new InMemoryRunner(agent, APP_NAME);
        try {
            Session session = runner.sessionService().createSession(APP_NAME, USER_ID).blockingGet();
            Content message = Content.fromParts(Part.fromText(call.userPrompt()));
            List<Event> events = runner.runAsync(USER_ID, session.id(), message)
                    .timeout(call.timeoutMs(), TimeUnit.MILLISECONDS)
                    .toList()
                    .blockingGet();
            for (Event event : events) {
                if (event.errorCode().isPresent() || event.errorMessage().isPresent()) {
                    LOGGER.warn("ADK agent {} returned an error event: code={}", call.agentName(),
                            event.errorCode().map(Object::toString).orElse("none"));
                    throw new AgentException(AgentException.Category.MODEL_UNAVAILABLE, "The model returned an error");
                }
            }
            String text = events.stream()
                    .filter(Event::finalResponse)
                    .reduce((first, second) -> second)
                    .flatMap(Event::content)
                    .map(Content::text)
                    .orElse(null);
            if (text == null || text.isBlank()) {
                throw new AgentException(AgentException.Category.INVALID_OUTPUT, "The model returned no output");
            }
            return text;
        } catch (AgentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw classify(call.agentName(), exception);
        }
    }

    private AgentException classify(String agentName, RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof TimeoutException || current instanceof java.net.SocketTimeoutException
                    || current instanceof java.net.http.HttpTimeoutException) {
                return new AgentException(AgentException.Category.MODEL_TIMEOUT, "The model call timed out");
            }
            if (current instanceof GeminiServiceException.MissingApiKey) {
                return new AgentException(AgentException.Category.MISSING_API_KEY, "Gemini API key is not configured");
            }
            if (current instanceof com.google.genai.errors.ClientException clientException) {
                int code = clientException.code();
                if (code == 401 || code == 403) {
                    return new AgentException(AgentException.Category.AUTH_FAILED, "Gemini rejected the configured credentials");
                }
                if (code == 429) {
                    return new AgentException(AgentException.Category.RATE_LIMITED, "Gemini rate limit reached");
                }
                return new AgentException(AgentException.Category.INVALID_OUTPUT, "Gemini rejected the request (" + code + ")");
            }
            if (current instanceof com.google.genai.errors.ServerException) {
                return new AgentException(AgentException.Category.MODEL_UNAVAILABLE, "Gemini is temporarily unavailable");
            }
            current = current.getCause();
        }
        LOGGER.warn("ADK agent {} failed: category={}", agentName, exception.getClass().getSimpleName());
        return new AgentException(AgentException.Category.MODEL_UNAVAILABLE, "The model call failed");
    }

    /** Converts the JSON-schema style map into the Gemini Schema subset understood by ADK's outputSchema. */
    @SuppressWarnings("unchecked")
    static Schema toGenaiSchema(Map<String, Object> source) {
        Schema.Builder builder = Schema.builder();
        String type = String.valueOf(source.get("type")).toUpperCase(java.util.Locale.ROOT);
        builder.type(type);
        if (source.get("properties") instanceof Map<?, ?> properties) {
            Map<String, Schema> converted = new java.util.LinkedHashMap<>();
            properties.forEach((key, value) -> converted.put(String.valueOf(key), toGenaiSchema((Map<String, Object>) value)));
            builder.properties(converted);
            builder.propertyOrdering(List.copyOf(converted.keySet()));
        }
        if (source.get("required") instanceof List<?> required) {
            builder.required(required.stream().map(String::valueOf).toList());
        }
        if (source.get("items") instanceof Map<?, ?> items) {
            builder.items(toGenaiSchema((Map<String, Object>) items));
        }
        if (source.get("enum") instanceof List<?> values) {
            builder.enum_(values.stream().map(String::valueOf).toList());
        }
        if (source.get("minimum") instanceof Number minimum) {
            builder.minimum(minimum.doubleValue());
        }
        if (source.get("maximum") instanceof Number maximum) {
            builder.maximum(maximum.doubleValue());
        }
        if (source.get("maxItems") instanceof Number maxItems) {
            builder.maxItems(maxItems.longValue());
        }
        if (source.get("maxLength") instanceof Number maxLength) {
            builder.maxLength(maxLength.longValue());
        }
        return builder.build();
    }
}

package com.impactiq.service;

import com.google.genai.Client;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.HttpOptions;
import com.google.genai.gaos.models.errors.GaosClientException;
import com.google.genai.gaos.models.errors.GaosServerException;
import com.google.genai.gaos.models.interactions.CreateModelInteraction;
import com.google.genai.gaos.models.interactions.CreateModelInteractionResponseFormat;
import com.google.genai.gaos.models.interactions.GenerationConfig;
import com.google.genai.gaos.models.interactions.Interaction;
import com.google.genai.gaos.models.interactions.InteractionsInput;
import com.google.genai.gaos.models.interactions.ModelOutputStep;
import com.google.genai.gaos.models.interactions.ResponseFormat;
import com.google.genai.gaos.models.interactions.Step;
import com.google.genai.gaos.models.interactions.TextContent;
import com.google.genai.gaos.models.interactions.TextResponseFormat;
import com.google.genai.gaos.models.interactions.TextResponseFormatMimeType;
import com.google.genai.gaos.models.operations.CreateInteractionRequestBody;
import com.impactiq.config.GeminiProperties;
import com.impactiq.exception.GeminiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class GeminiService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeminiService.class);
    /** Output budget for the original single-call impact analysis. */
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 1400;

    private final GeminiProperties properties;

    public GeminiService(GeminiProperties properties) {
        this.properties = properties;
    }

    public String sendTestPrompt(String prompt) {
        return send(prompt, null, null, DEFAULT_MAX_OUTPUT_TOKENS);
    }

    public String sendStructuredPrompt(String systemInstruction, String prompt, Map<String, Object> responseSchema) {
        return sendStructuredPrompt(systemInstruction, prompt, responseSchema, DEFAULT_MAX_OUTPUT_TOKENS);
    }

    public String sendStructuredPrompt(
            String systemInstruction,
            String prompt,
            Map<String, Object> responseSchema,
            int maxOutputTokens
    ) {
        Objects.requireNonNull(systemInstruction, "systemInstruction is required");
        Objects.requireNonNull(responseSchema, "responseSchema is required");
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be greater than zero");
        }
        return send(prompt, systemInstruction, responseSchema, maxOutputTokens);
    }

    private String send(String prompt, String systemInstruction, Map<String, Object> responseSchema, int maxOutputTokens) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt must not be blank");
        }

        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new GeminiServiceException.MissingApiKey();
        }
        if (properties.getModel() == null || properties.getModel().isBlank()) {
            throw new IllegalStateException("Gemini model is not configured");
        }
        if (properties.getTimeoutMs() <= 0) {
            throw new IllegalStateException("Gemini timeout must be greater than zero");
        }

        try (Client client = Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder().timeout(properties.getTimeoutMs()).build())
                .build()) {
            CreateModelInteraction.Builder request = CreateModelInteraction.builder()
                    .model(properties.getModel())
                    .input(InteractionsInput.of(prompt))
                    .generationConfig(GenerationConfig.builder()
                            .maxOutputTokens(maxOutputTokens)
                            .build())
                    .store(false);
            if (systemInstruction != null) {
                request.systemInstruction(systemInstruction);
            }
            if (responseSchema != null) {
                TextResponseFormat format = TextResponseFormat.builder()
                        .mimeType(TextResponseFormatMimeType.APPLICATION_JSON)
                        .schema(responseSchema)
                        .build();
                request.responseFormat(CreateModelInteractionResponseFormat.of(ResponseFormat.of(format)));
            }
            var response = client.interactions.create(CreateInteractionRequestBody.of(request.build()));
            String responseText = response.interaction()
                    .map(interaction -> extractOutputText(
                            interaction.outputText().orElse(null),
                            interaction.steps().orElse(null)))
                    .orElse(null);
            if (responseText == null || responseText.isBlank()) {
                Interaction interaction = response.interaction().orElse(null);
                LOGGER.warn("Gemini interaction returned no text: httpStatus={} status={} stepTypes={} errorCodes={}",
                        response.statusCode(),
                        interaction == null ? "missing" : interaction.status().map(value -> value.value()).orElse("missing"),
                        interaction == null ? List.of() : interaction.steps().orElse(List.of()).stream().map(Step::type).toList(),
                        interaction == null ? List.of() : interaction.errors().orElse(List.of()).stream()
                                .map(error -> error.code().orElse("unknown"))
                                .toList());
                throw new GeminiServiceException.MalformedResponse();
            }
            return responseText;
        } catch (GaosClientException | GaosServerException exception) {
            throw mapApiFailure(exception.code());
        } catch (GenAiIOException exception) {
            if (causedByTimeout(exception)) {
                throw new GeminiServiceException.RequestTimeout();
            }
            throw new GeminiServiceException.ApiFailure();
        } catch (GeminiServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.warn("Gemini Interactions client failed before producing output: exceptionType={} causeType={}",
                    exception.getClass().getSimpleName(),
                    exception.getCause() == null ? "none" : exception.getCause().getClass().getSimpleName());
            if (causedByTimeout(exception)) {
                throw new GeminiServiceException.RequestTimeout();
            }
            throw new GeminiServiceException.ApiFailure();
        }
    }

    private boolean causedByTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SocketTimeoutException
                    || current instanceof HttpTimeoutException
                    || current instanceof InterruptedIOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private GeminiServiceException mapApiFailure(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return new GeminiServiceException.AuthenticationFailure();
        }
        if (statusCode == 408 || statusCode == 504) {
            return new GeminiServiceException.RequestTimeout();
        }
        return new GeminiServiceException.ApiFailure(statusCode);
    }

    static String extractOutputText(String outputText, List<Step> steps) {
        if (outputText != null && !outputText.isBlank()) {
            return outputText;
        }
        if (steps == null) {
            return null;
        }
        return steps.stream()
                .filter(ModelOutputStep.class::isInstance)
                .map(ModelOutputStep.class::cast)
                .flatMap(step -> step.content().orElseGet(List::of).stream())
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .map(content -> content.text().orElse(""))
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining());
    }
}
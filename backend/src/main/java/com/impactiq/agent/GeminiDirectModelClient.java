package com.impactiq.agent;

import com.impactiq.config.GeminiProperties;
import com.impactiq.exception.GeminiServiceException;
import com.impactiq.service.GeminiService;

/** Fallback runtime: calls the existing GeminiService (Interactions API) with the agent's schema. */
public class GeminiDirectModelClient implements AgentModelClient {

    private final GeminiService geminiService;
    private final GeminiProperties properties;

    public GeminiDirectModelClient(GeminiService geminiService, GeminiProperties properties) {
        this.geminiService = geminiService;
        this.properties = properties;
    }

    @Override
    public String engine() {
        return "GEMINI";
    }

    @Override
    public String model() {
        return properties.getModel();
    }

    @Override
    public String generateJson(AgentCall call) {
        try {
            return geminiService.sendStructuredPrompt(call.systemInstruction(), call.userPrompt(),
                    call.responseSchema(), call.maxOutputTokens());
        } catch (GeminiServiceException.MissingApiKey exception) {
            throw new AgentException(AgentException.Category.MISSING_API_KEY, "Gemini API key is not configured");
        } catch (GeminiServiceException.AuthenticationFailure exception) {
            throw new AgentException(AgentException.Category.AUTH_FAILED, "Gemini rejected the configured credentials");
        } catch (GeminiServiceException.RequestTimeout exception) {
            throw new AgentException(AgentException.Category.MODEL_TIMEOUT, "The model call timed out");
        } catch (GeminiServiceException.MalformedResponse exception) {
            throw new AgentException(AgentException.Category.INVALID_OUTPUT, "The model returned no usable output");
        } catch (GeminiServiceException.ApiFailure exception) {
            if (exception.statusCode() == 429) {
                throw new AgentException(AgentException.Category.RATE_LIMITED, "Gemini rate limit reached");
            }
            throw new AgentException(AgentException.Category.MODEL_UNAVAILABLE, "Gemini is temporarily unavailable");
        }
    }
}

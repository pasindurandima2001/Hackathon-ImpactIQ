package com.impactiq.service;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.impactiq.config.GeminiProperties;
import com.impactiq.exception.GeminiServiceException;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * One shared Gemini {@link Client} for embeddings and the ADK agents. The API key is read from
 * configuration (environment / .env) and never logged or returned to callers.
 */
@Component
public class GeminiClientFactory {

    private final GeminiProperties properties;
    private Client client;

    public GeminiClientFactory(GeminiProperties properties) {
        this.properties = properties;
    }

    public boolean isConfigured() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    public synchronized Client client() {
        if (!isConfigured()) {
            throw new GeminiServiceException.MissingApiKey();
        }
        if (client == null) {
            client = Client.builder()
                    .apiKey(properties.getApiKey())
                    .httpOptions(HttpOptions.builder().timeout(properties.getTimeoutMs()).build())
                    .build();
        }
        return client;
    }

    public String model() {
        return properties.getModel();
    }

    public int timeoutMs() {
        return properties.getTimeoutMs();
    }

    @PreDestroy
    public synchronized void close() {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}

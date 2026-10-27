package com.impactiq.service;

import com.impactiq.config.GeminiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertFalse;

class GeminiConnectivityTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
    void sendsOneSafeConnectivityPrompt() {
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey(System.getenv("GEMINI_API_KEY"));
        properties.setModel(System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.1-flash-lite"));

        String response = new GeminiService(properties).sendTestPrompt("Reply with only the word OK.");

        assertFalse(response.isBlank());
    }
}

package com.impactiq.service;

import com.impactiq.config.GeminiProperties;
import com.impactiq.exception.GeminiServiceException;
import com.google.genai.gaos.models.interactions.ModelOutputStep;
import com.google.genai.gaos.models.interactions.Step;
import com.google.genai.gaos.models.interactions.TextContent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeminiServiceTest {

    @Test
    void missingApiKeyFailsBeforeCallingGoogle() {
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey(" ");
        GeminiService service = new GeminiService(properties);

        assertThrows(GeminiServiceException.MissingApiKey.class,
                () -> service.sendTestPrompt("Reply with OK"));
    }

    @Test
    void blankPromptIsRejected() {
        GeminiProperties properties = new GeminiProperties();
        GeminiService service = new GeminiService(properties);

        assertThrows(IllegalArgumentException.class, () -> service.sendTestPrompt(" "));
    }

    @Test
    void extractsOutputFromModelOutputSteps() {
        List<Step> steps = List.of(ModelOutputStep.builder()
                .content(List.of(TextContent.builder().text("{\"ok\":true}").build()))
                .build());

        assertEquals("{\"ok\":true}", GeminiService.extractOutputText(null, steps));
    }
}

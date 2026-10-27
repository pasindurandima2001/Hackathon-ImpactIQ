package com.impactiq.agent;

import com.impactiq.agent.AgentContracts.MlSignal;
import com.impactiq.config.MlProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Calls the trained triage model service (ml/serve.py). The prediction is a statistical, advisory signal for the
 * Defect Triage Agent; any failure returns empty so the investigation continues without it.
 */
@Component
public class MlTriageClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(MlTriageClient.class);
    private static final int MAX_TITLE_CHARS = 500;
    private static final int MAX_DESCRIPTION_CHARS = 5000;

    private final MlProperties properties;
    private final RestClient restClient;

    public MlTriageClient(MlProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public Optional<MlSignal> predict(String title, String description) {
        if (!properties.isEnabled() || properties.getUrl() == null || properties.getUrl().isBlank()
                || title == null || title.isBlank()) {
            return Optional.empty();
        }
        try {
            MlSignal signal = restClient.post()
                    .uri(properties.getUrl().replaceAll("/+$", "") + "/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("title", truncate(title, MAX_TITLE_CHARS),
                            "description", truncate(description == null ? "" : description, MAX_DESCRIPTION_CHARS)))
                    .retrieve()
                    .body(MlSignal.class);
            return Optional.ofNullable(signal).filter(value -> value.severity() != null && !value.severity().isEmpty());
        } catch (RestClientException exception) {
            LOGGER.warn("ML triage model unavailable: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}

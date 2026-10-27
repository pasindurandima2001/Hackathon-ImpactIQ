package com.impactiq.agent;

import com.impactiq.config.GeminiProperties;
import com.impactiq.config.InvestigationProperties;
import com.impactiq.service.GeminiClientFactory;
import com.impactiq.service.GeminiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
public class AgentConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentConfig.class);

    /** Selects the agent runtime from impactiq.investigation.engine (adk | gemini). */
    @Bean
    public AgentModelClient agentModelClient(
            InvestigationProperties properties,
            GeminiClientFactory clientFactory,
            GeminiService geminiService,
            GeminiProperties geminiProperties
    ) {
        String engine = properties.getEngine() == null ? "adk" : properties.getEngine().trim().toLowerCase(Locale.ROOT);
        AgentModelClient client = switch (engine) {
            case "gemini" -> new GeminiDirectModelClient(geminiService, geminiProperties);
            case "adk" -> new AdkAgentModelClient(clientFactory, properties.getThinkingLevel());
            default -> throw new IllegalStateException("impactiq.investigation.engine must be 'adk' or 'gemini'");
        };
        LOGGER.info("Investigation agent runtime: {}", client.engine());
        return client;
    }
}

package com.impactiq.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactiq.entity.AppUser;
import com.impactiq.entity.DocumentStatus;
import com.impactiq.repository.AppUserRepository;
import com.impactiq.repository.DocumentVersionRepository;
import com.impactiq.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Opt-in live check of the chatbot against the real agent runtime (Google ADK + Gemini) on an H2 database.
 * Never part of the default build. Run with IMPACTIQ_LIVE_AI=true; the API key comes from configuration (.env).
 */
@SpringBootTest(properties = {TestProperties.STORAGE_DIR, TestProperties.NO_BOOTSTRAP_ADMIN})
@AutoConfigureTestDatabase
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "IMPACTIQ_LIVE_AI", matches = "true")
class LiveChatAssistantTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private DocumentVersionRepository versionRepository;

    @Test
    void liveModelAnswersWithVerifiedCitationsAndResolvesFollowUps() throws Exception {
        AppUser user = new AppUser();
        user.setUsername("live-chat-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash("{noop}unused");
        user.setRole(com.impactiq.entity.UserRole.ADMIN); // project knowledge is managed by administrators
        AppUserPrincipal principal = new AppUserPrincipal(userRepository.saveAndFlush(user));

        long projectId = postJson(principal, "/api/projects", Map.of("name", "Live chat " + UUID.randomUUID())).path("id").asLong();
        MockHttpServletRequestBuilder upload = multipart("/api/projects/" + projectId + "/documents")
                .file(new MockMultipartFile("file", "payments-runbook.md", "text/plain", """
                        # Payments runbook
                        PaymentService.charge must reject requests without a currency code.
                        Since release 4.2 the Android client omits the currency field for EUR payments.
                        A NullPointerException in PaymentService.charge means the currency was null.
                        """.getBytes(StandardCharsets.UTF_8)));
        long documentId = objectMapper.readTree(mockMvc.perform(as(principal, upload)).andReturn().getResponse().getContentAsString())
                .path("id").asLong();
        Instant deadline = Instant.now().plus(Duration.ofSeconds(120));
        while (Instant.now().isBefore(deadline)
                && versionRepository.findByDocumentIdAndVersionNumber(documentId, 1).map(v -> v.getStatus() != DocumentStatus.INDEXED).orElse(true)) {
            Thread.sleep(500);
        }

        long conversationId = postJson(principal, "/api/chat/conversations", Map.of("projectId", projectId))
                .path("conversation").path("id").asLong();
        JsonNode first = postJson(principal, "/api/chat/conversations/" + conversationId + "/messages",
                Map.of("content", "Why does PaymentService.charge throw a NullPointerException?")).path("assistantMessage");
        System.out.println("Live chat answer status: " + first.path("answerStatus").asText() + ", citations: " + first.path("citations").size());
        assertEquals("COMPLETE", first.path("status").asText(), first.toString());
        assertTrue(List.of("GROUNDED", "PARTIAL").contains(first.path("answerStatus").asText()), first.toString());
        assertTrue(first.path("citations").size() >= 1, "Live answer cites the runbook");
        assertEquals("payments-runbook.md", first.path("citations").get(0).path("source").path("filename").asText());

        JsonNode followUp = postJson(principal, "/api/chat/conversations/" + conversationId + "/messages",
                Map.of("content", "Which client release introduced that?")).path("assistantMessage");
        assertEquals("COMPLETE", followUp.path("status").asText(), followUp.toString());
        assertTrue(followUp.path("content").asText().contains("4.2"), "Follow-up resolved from history: " + followUp.path("content").asText());
    }

    private MockHttpServletRequestBuilder as(AppUserPrincipal user, MockHttpServletRequestBuilder request) {
        return request.with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()))).with(csrf());
    }

    private JsonNode postJson(AppUserPrincipal user, String url, Object body) throws Exception {
        String response = mockMvc.perform(as(user, post(url)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body))).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }
}

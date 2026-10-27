package com.impactiq.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactiq.agent.AgentModelClient;
import com.impactiq.entity.AppUser;
import com.impactiq.entity.DocumentStatus;
import com.impactiq.entity.InvestigationStatus;
import com.impactiq.repository.AppUserRepository;
import com.impactiq.repository.DocumentVersionRepository;
import com.impactiq.repository.InvestigationRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Opt-in live check of the real agent runtime (Google ADK + Gemini) and Gemini embeddings on an H2 database.
 * Run with IMPACTIQ_LIVE_AI=true; the API key is read from configuration (.env), never from this test.
 */
@SpringBootTest(properties = {TestProperties.STORAGE_DIR, TestProperties.NO_BOOTSTRAP_ADMIN})
@AutoConfigureTestDatabase
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "IMPACTIQ_LIVE_AI", matches = "true")
class LiveAgentInvestigationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private InvestigationRepository investigationRepository;
    @Autowired
    private DocumentVersionRepository versionRepository;
    @Autowired
    private AgentModelClient agentModelClient;

    @Test
    void realAgentsProduceAGroundedReport() throws Exception {
        AppUser user = new AppUser();
        user.setUsername("live-" + System.nanoTime());
        user.setPasswordHash("unused");
        user.setRole(com.impactiq.entity.UserRole.ADMIN); // project knowledge is managed by administrators
        AppUserPrincipal principal = new AppUserPrincipal(userRepository.saveAndFlush(user));

        long projectId = json(as(principal, post("/api/projects")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Live " + System.nanoTime() + "\"}")).path("id").asLong();
        long documentId = json(as(principal, multipart("/api/projects/" + projectId + "/documents")
                .file(new MockMultipartFile("file", "payments-runbook.md", "text/markdown", """
                        # Payments runbook
                        PaymentService.charge must reject requests without a currency code.
                        Since release 4.2 the currency field is optional in the mobile client, which sends null for EUR.
                        """.getBytes(StandardCharsets.UTF_8))))).path("id").asLong();
        Instant indexDeadline = Instant.now().plus(Duration.ofSeconds(60));
        while (versionRepository.findByDocumentIdAndVersionNumber(documentId, 1).orElseThrow().getStatus() != DocumentStatus.INDEXED) {
            assertTrue(Instant.now().isBefore(indexDeadline), "Document indexing timed out");
            assertTrue(versionRepository.findByDocumentIdAndVersionNumber(documentId, 1).orElseThrow().getStatus() != DocumentStatus.FAILED);
            Thread.sleep(250);
        }

        long defectId = json(as(principal, post("/api/defects")).contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"Mobile checkout fails with HTTP 500","description":"Since release 4.2 mobile users paying in EUR get a 500 error.",
                 "environment":"PRODUCTION","service":"payments","affectedUsers":800,"frequency":"HIGH",
                 "businessCriticality":"CRITICAL","customerImpact":"HIGH","technicalImpact":"HIGH","projectId":%d}
                """.formatted(projectId))).path("id").asLong();
        json(as(principal, post("/api/defects/" + defectId + "/evidence")).contentType(MediaType.APPLICATION_JSON).content("""
                {"type":"STACK_TRACE","label":"API stack trace","content":"java.lang.NullPointerException: currency\\n\\tat com.shop.PaymentService.charge(PaymentService.java:42)\\n\\tat com.shop.CheckoutController.pay(CheckoutController.java:88)"}
                """));

        long investigationId = json(as(principal, post("/api/defects/" + defectId + "/investigations")))
                .path("summary").path("id").asLong();
        Instant deadline = Instant.now().plus(Duration.ofMinutes(5));
        while (InvestigationStatus.ACTIVE.contains(investigationRepository.findById(investigationId).orElseThrow().getStatus())) {
            assertTrue(Instant.now().isBefore(deadline), "Live investigation timed out");
            Thread.sleep(1000);
        }
        JsonNode run = json(as(principal, get("/api/defects/" + defectId + "/investigations/" + investigationId)));
        System.out.println("LIVE engine=" + agentModelClient.engine() + " status=" + run.path("summary").path("status").asText()
                + " steps=" + run.path("steps").findValuesAsText("status")
                + " hypotheses=" + run.path("report").path("hypotheses").size()
                + " retrieval=" + run.path("report").path("limitations").path("retrievalMode").asText()
                + " error=" + run.path("summary").path("errorCategory").asText());
        assertEquals("COMPLETED", run.path("summary").path("status").asText());
        assertTrue(run.path("report").path("hypotheses").size() >= 1);
        run.path("report").path("hypotheses").forEach(h -> assertEquals("UNCONFIRMED", h.path("status").asText()));
    }

    private <T extends MockHttpServletRequestBuilder> T as(AppUserPrincipal user, T request) {
        request.with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()))).with(csrf());
        return request;
    }

    private JsonNode json(MockHttpServletRequestBuilder request) throws Exception {
        return objectMapper.readTree(mockMvc.perform(request).andReturn().getResponse().getContentAsString());
    }
}

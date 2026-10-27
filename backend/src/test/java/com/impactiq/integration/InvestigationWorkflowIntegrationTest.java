package com.impactiq.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactiq.agent.AgentException;
import com.impactiq.agent.AgentModelClient;
import com.impactiq.entity.AppUser;
import com.impactiq.entity.DocumentStatus;
import com.impactiq.entity.InvestigationStatus;
import com.impactiq.entity.UserRole;
import com.impactiq.repository.AppUserRepository;
import com.impactiq.repository.DocumentChunkRepository;
import com.impactiq.repository.DocumentVersionRepository;
import com.impactiq.repository.InvestigationRepository;
import com.impactiq.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end backend workflow on H2 with a scripted agent runtime: knowledge upload → indexing → project-scoped
 * retrieval → multi-agent investigation → cited report → feedback; plus isolation, failure and retry behaviour.
 */
@SpringBootTest(properties = {
        TestProperties.NO_GEMINI_KEY,
        TestProperties.NO_AUTO_COMPONENTS,
        TestProperties.NO_EMBEDDINGS,
        TestProperties.STORAGE_DIR,
        TestProperties.NO_BOOTSTRAP_ADMIN,
        TestProperties.FAST_RETRIES
})
@AutoConfigureTestDatabase
@AutoConfigureMockMvc
class InvestigationWorkflowIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    private static final String TRIAGE = """
            {"category":"FUNCTIONAL","summary":"Checkout fails with a NullPointerException in PaymentService",
             "affectedComponent":"payments","technicalSeverityAssessment":"HIGH",
             "keySignals":["NullPointerException"],"missingInformation":["Deployment version"]}""";
    private static final String EVIDENCE = """
            {"findings":[{"evidenceId":"E1","observation":"NPE thrown in PaymentService.charge","significance":"HIGH","location":"line 2"},
                         {"evidenceId":"E99","observation":"Invented evidence","significance":"LOW","location":""}],
             "evidenceGaps":["No request payload captured"]}""";
    private static final String ROOT_CAUSE = """
            {"hypotheses":[
               {"title":"Unsupported","description":"Cites nothing real","likelihood":"HIGH","confidence":90,
                "supportingEvidenceIds":["Z9"],"conflictingEvidenceIds":[],"reasoning":"none"},
               {"title":"Currency is null on the payment request","description":"Currency is not validated before charge",
                "likelihood":"HIGH","confidence":72,"supportingEvidenceIds":["E1","K1"],"conflictingEvidenceIds":[],
                "reasoning":"Stack trace and the runbook both point to currency handling"}],
             "limitations":["Single stack trace"]}""";
    private static final String FIX = """
            {"fixSteps":[{"action":"Validate currency before calling the gateway","rationale":"Prevents the NPE",
                          "hypothesisRank":1,"risk":"Rejects previously accepted requests"}],
             "risks":["Behaviour change for clients sending no currency"],
             "verificationTests":[{"name":"Charge without currency returns 400","type":"UNIT","steps":"Call charge with null currency","expectedResult":"400 response"}],
             "regressionTests":[{"name":"Valid charge still succeeds","type":"INTEGRATION","steps":"Charge with EUR","expectedResult":"Payment captured"}],
             "additionalEvidenceNeeded":["Gateway logs"]}""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private InvestigationRepository investigationRepository;
    @Autowired
    private DocumentVersionRepository versionRepository;
    @Autowired
    private DocumentChunkRepository chunkRepository;

    @MockitoBean
    private AgentModelClient agentModelClient;

    private final AtomicInteger rootCauseFailures = new AtomicInteger();
    private final List<String> prompts = new ArrayList<>();
    private AppUserPrincipal admin;
    private AppUserPrincipal owner;
    private AppUserPrincipal outsider;

    @BeforeEach
    void setUp() {
        admin = principal("admin-" + UUID.randomUUID().toString().substring(0, 8), UserRole.ADMIN);
        owner = principal("owner-" + UUID.randomUUID().toString().substring(0, 8));
        outsider = principal("outsider-" + UUID.randomUUID().toString().substring(0, 8));
        rootCauseFailures.set(0);
        prompts.clear();
        when(agentModelClient.engine()).thenReturn("TEST");
        when(agentModelClient.model()).thenReturn("scripted");
        when(agentModelClient.generateJson(any())).thenAnswer(invocation -> {
            AgentModelClient.AgentCall call = invocation.getArgument(0);
            synchronized (prompts) {
                prompts.add(call.userPrompt());
            }
            return switch (call.agentName()) {
                case "defect_triage_agent" -> TRIAGE;
                case "evidence_analysis_agent" -> EVIDENCE;
                case "root_cause_analysis_agent" -> {
                    if (rootCauseFailures.getAndDecrement() > 0) {
                        throw new AgentException(AgentException.Category.MODEL_TIMEOUT, "The model call timed out");
                    }
                    yield ROOT_CAUSE;
                }
                case "fix_recommendation_agent" -> FIX;
                default -> throw new AgentException(AgentException.Category.INTERNAL, "unexpected agent");
            };
        });
    }

    @Test
    void fullInvestigationProducesCitedGroundedReport() throws Exception {
        long projectId = createProject(owner, "Payments " + UUID.randomUUID());
        long componentId = createComponent(admin, projectId, "checkout");
        long documentId = uploadDocument(admin, projectId, componentId, "runbook.md", """
                # Checkout runbook
                PaymentService.charge requires a currency code.
                A NullPointerException in PaymentService usually means the currency was not validated.
                """);
        awaitIndexed(documentId);

        long defectId = createDefect(owner, projectId, componentId);
        addEvidence(owner, defectId, """
                java.lang.NullPointerException: currency is null
                \tat com.shop.PaymentService.charge(PaymentService.java:42)
                password=SuperSecret123 apiKey: AIzaSyA1234567890abcdefghijklmnopqrstuv
                """);

        mockMvc.perform(as(owner, get("/api/defects/" + defectId + "/evidence")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].redactionCount").value(2))
                .andExpect(jsonPath("$[0].content").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SuperSecret123"))));

        long investigationId = startInvestigation(owner, defectId);
        JsonNode run = awaitFinished(owner, defectId, investigationId);

        assertEquals("COMPLETED", run.path("summary").path("status").asText());
        JsonNode report = run.path("report");
        JsonNode defect = getJson(owner, "/api/defects/" + defectId);
        assertEquals(defect.path("baselineScore").asInt(), report.path("impactScore").path("score").asInt(),
                "Report score must come from ImpactScoringService");
        assertEquals(defect.path("baselinePriority").asText(), report.path("defect").path("priority").asText());

        JsonNode hypotheses = report.path("hypotheses");
        assertEquals(1, hypotheses.size(), "Hypothesis without verifiable evidence must be discarded");
        assertEquals("UNCONFIRMED", hypotheses.get(0).path("status").asText());
        assertEquals(List.of("E1", "K1"), List.of(hypotheses.get(0).path("supportingEvidenceIds").get(0).asText(),
                hypotheses.get(0).path("supportingEvidenceIds").get(1).asText()));
        assertTrue(report.path("limitations").path("validationWarnings").toString().contains("E99"));

        JsonNode knowledge = findEvidence(report, "K1");
        assertEquals(documentId, knowledge.path("source").path("documentId").asLong());
        assertEquals("runbook.md", knowledge.path("source").path("filename").asText());
        assertEquals(1, knowledge.path("source").path("versionNumber").asInt());
        assertTrue(knowledge.path("source").path("startLine").asInt() >= 1);
        assertTrue(report.path("project").path("verified").asBoolean());

        // Secrets never reach the model; untrusted content is wrapped as data.
        synchronized (prompts) {
            assertFalse(prompts.stream().anyMatch(prompt -> prompt.contains("SuperSecret123")));
            assertTrue(prompts.stream().allMatch(prompt -> prompt.contains("<untrusted_data>")));
        }

        mockMvc.perform(as(owner, post("/api/defects/" + defectId + "/investigations/" + investigationId + "/feedback"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hypothesisRank\":1,\"verdict\":\"CONFIRMED\",\"comment\":\"Reproduced locally\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(as(owner, post("/api/defects/" + defectId + "/investigations/" + investigationId + "/feedback"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hypothesisRank\":4,\"verdict\":\"REJECTED\"}"))
                .andExpect(status().isBadRequest());

        JsonNode dashboard = getJson(owner, "/api/dashboard/metrics");
        assertTrue(dashboard.path("investigation").path("totalInvestigations").asInt() >= 1);
        assertTrue(dashboard.path("investigation").path("evidenceCoveragePercent").asInt() > 0);
        assertTrue(dashboard.path("investigation").path("pendingReviewCount").asInt() >= 0);
    }

    @Test
    void otherUsersDefectsAndKnowledgeManagementStayPrivate() throws Exception {
        long projectId = createProject(owner, "Private " + UUID.randomUUID());
        long documentId = uploadDocument(admin, projectId, null, "secret-design.txt", "Ledger reconciliation uses batch job LEDGER42.");
        awaitIndexed(documentId);
        long defectId = createDefect(owner, projectId, null);

        // Projects are readable by every user (for the chatbot), but defects and knowledge management are not.
        mockMvc.perform(as(outsider, get("/api/projects/" + projectId))).andExpect(status().isOk());
        mockMvc.perform(as(outsider, get("/api/projects/" + projectId + "/documents"))).andExpect(status().isForbidden());
        mockMvc.perform(as(outsider, post("/api/projects/" + projectId + "/search")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"LEDGER42 reconciliation\"}")).andExpect(status().isForbidden());
        mockMvc.perform(as(outsider, get("/api/defects/" + defectId))).andExpect(status().isNotFound());
        mockMvc.perform(as(outsider, post("/api/defects/" + defectId + "/investigations"))).andExpect(status().isNotFound());
        JsonNode visible = getJson(outsider, "/api/defects");
        visible.forEach(item -> assertFalse(item.path("id").asLong() == defectId));

        // Project knowledge is admin-only; even an admin searching another project never gets this project's chunks.
        long otherProject = createProject(outsider, "Outsider " + UUID.randomUUID());
        JsonNode search = postJson(admin, "/api/projects/" + otherProject + "/search", "{\"query\":\"LEDGER42 reconciliation\"}");
        assertEquals(0, search.path("results").size());
        JsonNode ownSearch = postJson(admin, "/api/projects/" + projectId + "/search", "{\"query\":\"LEDGER42 reconciliation\"}");
        assertTrue(ownSearch.path("results").size() >= 1);
        assertEquals("LEXICAL", ownSearch.path("mode").asText());
    }

    @Test
    void failedRootCauseCanBeRetriedAndReusesSuccessfulStages() throws Exception {
        long projectId = createProject(owner, "Retry " + UUID.randomUUID());
        long defectId = createDefect(owner, projectId, null);
        addEvidence(owner, defectId, "java.lang.NullPointerException\n\tat com.shop.PaymentService.charge(PaymentService.java:42)");
        rootCauseFailures.set(2); // both attempts of the first run time out

        long first = startInvestigation(owner, defectId);
        JsonNode failed = awaitFinished(owner, defectId, first);
        assertEquals("FAILED", failed.path("summary").path("status").asText());
        assertEquals("MODEL_TIMEOUT", failed.path("summary").path("errorCategory").asText());
        assertTrue(failed.path("summary").path("retryEligible").asBoolean());
        assertEquals("SKIPPED", stepStatus(failed, "fix_recommendation_agent"));
        assertEquals("FAILED", stepStatus(failed, "root_cause_analysis_agent"));

        JsonNode retried = postJson(owner, "/api/defects/" + defectId + "/investigations/" + first + "/retry", null);
        long second = retried.path("summary").path("id").asLong();
        JsonNode done = awaitFinished(owner, defectId, second);
        assertEquals("COMPLETED", done.path("summary").path("status").asText());
        assertEquals(2, done.path("summary").path("attempt").asInt());
        assertEquals("REUSED", stepStatus(done, "defect_triage_agent"));
        assertEquals("SUCCEEDED", stepStatus(done, "root_cause_analysis_agent"));

        mockMvc.perform(as(owner, post("/api/defects/" + defectId + "/investigations/" + second + "/retry")))
                .andExpect(status().isConflict());
    }

    @Test
    void documentVersionsReplaceIndexedChunksAndDeletionRemovesThem() throws Exception {
        long projectId = createProject(owner, "Docs " + UUID.randomUUID());
        long documentId = uploadDocument(admin, projectId, null, "guide.txt", "Original text about caching layer ALPHA.");
        awaitIndexed(documentId);

        MockMultipartHttpServletRequestBuilder version = multipart("/api/projects/" + projectId + "/documents/" + documentId + "/versions");
        version.file(new MockMultipartFile("file", "guide.txt", "text/plain", "Updated text about caching layer BETA.".getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(as(admin, version)).andExpect(status().isAccepted());
        awaitVersionIndexed(documentId, 2);

        JsonNode beta = postJson(admin, "/api/projects/" + projectId + "/search", "{\"query\":\"BETA caching\"}");
        assertEquals(2, beta.path("results").get(0).path("versionNumber").asInt());
        JsonNode alpha = postJson(admin, "/api/projects/" + projectId + "/search", "{\"query\":\"ALPHA\"}");
        assertEquals(0, alpha.path("results").size(), "Chunks of the superseded version must be removed");

        mockMvc.perform(as(admin, multipart("/api/projects/" + projectId + "/documents")
                        .file(new MockMultipartFile("file", "malware.exe", "application/octet-stream", new byte[]{77, 90, 0, 1}))))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(as(admin, multipart("/api/projects/" + projectId + "/documents")
                        .file(new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.UTF_8)))))
                .andExpect(status().isUnsupportedMediaType());

        mockMvc.perform(as(admin, delete("/api/projects/" + projectId + "/documents/" + documentId))).andExpect(status().isNoContent());
        assertEquals(0, chunkRepository.countByProjectId(projectId));
        assertTrue(versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId).isEmpty());
    }

    @Test
    void draftFallsBackToHeuristicWhenModelUnavailableAndPersistsNothing() throws Exception {
        doThrow(new AgentException(AgentException.Category.MISSING_API_KEY, "Gemini API key is not configured"))
                .when(agentModelClient).generateJson(any());
        long before = getJson(owner, "/api/defects").size();
        JsonNode draft = postJson(owner, "/api/defects/draft",
                "{\"mode\":\"PASTE_ERROR\",\"text\":\"Some context\\njava.lang.IllegalStateException: pool exhausted token=abcdef123456\"}");
        assertEquals("HEURISTIC", draft.path("source").asText());
        assertTrue(draft.path("title").asText().contains("IllegalStateException"));
        assertFalse(draft.path("evidence").path("content").asText().contains("abcdef123456"));
        assertEquals(before, getJson(owner, "/api/defects").size(), "Drafting must not persist a defect");
    }

    // ---- helpers ----

    private AppUserPrincipal principal(String username) {
        return principal(username, UserRole.USER);
    }

    private AppUserPrincipal principal(String username, UserRole role) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("irrelevant-password"));
        user.setRole(role);
        return new AppUserPrincipal(userRepository.saveAndFlush(user));
    }

    private <T extends MockHttpServletRequestBuilder> T as(AppUserPrincipal user, T request) {
        request.with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()))).with(csrf());
        return request;
    }

    private JsonNode getJson(AppUserPrincipal user, String url) throws Exception {
        String body = mockMvc.perform(as(user, get(url))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode postJson(AppUserPrincipal user, String url, String json) throws Exception {
        MockHttpServletRequestBuilder request = as(user, post(url));
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        String body = mockMvc.perform(request).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    /** Projects are created by an administrator, who makes the given user a project OWNER. */
    private long createProject(AppUserPrincipal user, String name) throws Exception {
        long projectId = postJson(admin, "/api/projects", "{\"name\":\"" + name + "\"}").path("id").asLong();
        postJson(admin, "/api/projects/" + projectId + "/members", "{\"username\":\"" + user.getUsername() + "\",\"role\":\"OWNER\"}");
        return projectId;
    }

    private long createComponent(AppUserPrincipal user, long projectId, String name) throws Exception {
        return postJson(user, "/api/projects/" + projectId + "/components", "{\"name\":\"" + name + "\"}").path("id").asLong();
    }

    private long uploadDocument(AppUserPrincipal user, long projectId, Long componentId, String filename, String content) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/projects/" + projectId + "/documents");
        request.file(new MockMultipartFile("file", filename, "text/plain", content.getBytes(StandardCharsets.UTF_8)));
        if (componentId != null) {
            request.param("componentId", String.valueOf(componentId));
        }
        String body = mockMvc.perform(as(user, request)).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("id").asLong();
    }

    private long createDefect(AppUserPrincipal user, long projectId, Long componentId) throws Exception {
        String body = """
                {"title":"Checkout payment fails","description":"Customers see a 500 error when paying. NullPointerException in PaymentService.",
                 "environment":"PRODUCTION","service":"payments","affectedUsers":250,"frequency":"HIGH",
                 "businessCriticality":"CRITICAL","customerImpact":"HIGH","technicalImpact":"HIGH",
                 "projectId":%d,"componentId":%s}""".formatted(projectId, componentId == null ? "null" : componentId);
        return postJson(user, "/api/defects", body).path("id").asLong();
    }

    private void addEvidence(AppUserPrincipal user, long defectId, String content) throws Exception {
        String json = objectMapper.writeValueAsString(java.util.Map.of("type", "STACK_TRACE", "label", "Server stack trace", "content", content));
        mockMvc.perform(as(user, post("/api/defects/" + defectId + "/evidence")).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private long startInvestigation(AppUserPrincipal user, long defectId) throws Exception {
        String body = mockMvc.perform(as(user, post("/api/defects/" + defectId + "/investigations")))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("summary").path("id").asLong();
    }

    private JsonNode awaitFinished(AppUserPrincipal user, long defectId, long investigationId) throws Exception {
        Instant deadline = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(deadline)) {
            InvestigationStatus status = investigationRepository.findById(investigationId).orElseThrow().getStatus();
            if (!InvestigationStatus.ACTIVE.contains(status)) {
                return getJson(user, "/api/defects/" + defectId + "/investigations/" + investigationId);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Investigation did not finish in time");
    }

    private void awaitIndexed(long documentId) throws InterruptedException {
        awaitVersionIndexed(documentId, 1);
    }

    private void awaitVersionIndexed(long documentId, int versionNumber) throws InterruptedException {
        Instant deadline = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(deadline)) {
            var version = versionRepository.findByDocumentIdAndVersionNumber(documentId, versionNumber);
            if (version.isPresent() && Set.of(DocumentStatus.INDEXED, DocumentStatus.FAILED).contains(version.get().getStatus())) {
                assertEquals(DocumentStatus.INDEXED, version.get().getStatus(), version.get().getErrorMessage());
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Document was not indexed in time");
    }

    private JsonNode findEvidence(JsonNode report, String id) {
        for (JsonNode item : report.path("evidence")) {
            if (id.equals(item.path("id").asText())) {
                return item;
            }
        }
        throw new AssertionError("Evidence " + id + " missing from report");
    }

    private JsonNode step(JsonNode run, String agent) {
        for (JsonNode step : run.path("steps")) {
            if (agent.equals(step.path("agentName").asText())) {
                return step;
            }
        }
        throw new AssertionError("Step " + agent + " missing");
    }

    private String stepStatus(JsonNode run, String agent) {
        JsonNode step = step(run, agent);
        assertNotNull(step);
        return step.path("status").asText();
    }
}

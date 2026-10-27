package com.impactiq.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactiq.agent.AgentException;
import com.impactiq.agent.AgentModelClient;
import com.impactiq.agent.ComponentDiscoveryAgent;
import com.impactiq.entity.AppUser;
import com.impactiq.entity.ProjectComponent;
import com.impactiq.entity.UserRole;
import com.impactiq.repository.AppUserRepository;
import com.impactiq.repository.ProjectComponentRepository;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AI component discovery on H2 with a scripted model (never Gemini): automatic after indexing, and on demand. */
@SpringBootTest(properties = {
        TestProperties.NO_GEMINI_KEY,
        TestProperties.NO_EMBEDDINGS,
        TestProperties.STORAGE_DIR,
        TestProperties.NO_BOOTSTRAP_ADMIN,
        TestProperties.FAST_RETRIES
})
@AutoConfigureTestDatabase
@AutoConfigureMockMvc
class ComponentDiscoveryIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final String DOCUMENT = """
            # Checkout architecture
            The Payment Service charges cards and calls the Fraud Checker before capture.
            The Android App sends orders to the Order API.
            """;
    private static final String REPLY = """
            {"components":[
              {"name":"Payment Service","description":"Charges cards.","sourceIds":["S1"]},
              {"name":"Fraud Checker","description":"Scores payment risk.","sourceIds":["S1"]},
              {"name":"Inventory Service","description":"Invented by the model.","sourceIds":["S1"]}
            ]}""";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ProjectComponentRepository componentRepository;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("componentDiscoveryExecutor")
    private org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor discoveryExecutor;

    @MockitoBean
    private AgentModelClient agentModelClient;

    private final AtomicReference<Object> nextReply = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        nextReply.set(REPLY);
        when(agentModelClient.engine()).thenReturn("TEST");
        when(agentModelClient.model()).thenReturn("scripted");
        when(agentModelClient.generateJson(any())).thenAnswer(invocation -> {
            AgentModelClient.AgentCall call = invocation.getArgument(0);
            if (!ComponentDiscoveryAgent.NAME.equals(call.agentName())) {
                throw new AgentException(AgentException.Category.INTERNAL, "unexpected agent");
            }
            Object reply = nextReply.get();
            if (reply instanceof AgentException exception) {
                throw exception;
            }
            return reply;
        });
    }

    @Test
    void indexedDocumentAddsGroundedComponentsAutomatically() throws Exception {
        AppUserPrincipal owner = principal(UserRole.ADMIN);
        long projectId = createProject(owner);
        upload(owner, projectId);

        List<String> names = awaitComponents(projectId, 2);
        assertEquals(List.of("Fraud Checker", "Payment Service"), names);
        ProjectComponent payment = componentRepository.findByProjectIdOrderByNameAsc(projectId).get(1);
        assertTrue(payment.getDescription().contains("Detected by AI from architecture.md"), payment.getDescription());

        // On demand: everything is already present, so nothing new is added and nothing is duplicated.
        mockMvc.perform(as(owner, post("/api/projects/" + projectId + "/components/discover")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added.length()").value(0))
                .andExpect(jsonPath("$.documentsRead").value(1));
        assertEquals(2, componentRepository.findByProjectIdOrderByNameAsc(projectId).size());
    }

    @Test
    void onDemandDetectionNeedsAdminAndReportsMissingModel() throws Exception {
        AppUserPrincipal owner = principal(UserRole.ADMIN);
        AppUserPrincipal viewer = principal(UserRole.USER);
        long projectId = createProject(owner);
        postJson(owner, "/api/projects/" + projectId + "/members", "{\"username\":\"" + viewer.getUsername() + "\",\"role\":\"VIEWER\"}");

        mockMvc.perform(as(owner, post("/api/projects/" + projectId + "/components/discover"))).andExpect(status().isBadRequest());

        nextReply.set(new AgentException(AgentException.Category.MISSING_API_KEY, "Gemini API key is not configured"));
        upload(owner, projectId);
        mockMvc.perform(as(viewer, post("/api/projects/" + projectId + "/components/discover"))).andExpect(status().isForbidden());
        mockMvc.perform(as(owner, post("/api/projects/" + projectId + "/components/discover")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not configured")));

        awaitDiscoveryIdle();
        assertTrue(componentRepository.findByProjectIdOrderByNameAsc(projectId).isEmpty(), "No model, no components");
        nextReply.set(REPLY);
        mockMvc.perform(as(owner, post("/api/projects/" + projectId + "/components/discover")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added.length()").value(2))
                .andExpect(jsonPath("$.added[0].name").value("Payment Service"));
    }

    private AppUserPrincipal principal(UserRole role) {
        AppUser user = new AppUser();
        user.setUsername("discover-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash(passwordEncoder.encode("unused-password"));
        user.setRole(role);
        return new AppUserPrincipal(userRepository.saveAndFlush(user));
    }

    private <T extends MockHttpServletRequestBuilder> T as(AppUserPrincipal user, T request) {
        request.with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()))).with(csrf());
        return request;
    }

    private JsonNode postJson(AppUserPrincipal user, String url, String body) throws Exception {
        String response = mockMvc.perform(as(user, post(url)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private long createProject(AppUserPrincipal user) throws Exception {
        return postJson(user, "/api/projects", "{\"name\":\"Discovery " + UUID.randomUUID() + "\"}").path("id").asLong();
    }

    private void upload(AppUserPrincipal user, long projectId) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/projects/" + projectId + "/documents");
        request.file(new MockMultipartFile("file", "architecture.md", "text/plain", DOCUMENT.getBytes(StandardCharsets.UTF_8)));
        mockMvc.perform(as(user, request)).andExpect(status().isAccepted());
        awaitIndexed(user, projectId);
    }

    private void awaitIndexed(AppUserPrincipal user, long projectId) throws Exception {
        Instant deadline = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(deadline)) {
            String body = mockMvc.perform(as(user, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .get("/api/projects/" + projectId))).andReturn().getResponse().getContentAsString();
            if (objectMapper.readTree(body).path("indexedDocumentCount").asLong() > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Document was not indexed in time");
    }

    /** Waits until the background discovery triggered by the upload has finished. */
    private void awaitDiscoveryIdle() throws InterruptedException {
        Thread.sleep(300); // the event is published just after the index commit becomes visible
        Instant deadline = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(deadline)) {
            if (discoveryExecutor.getActiveCount() == 0 && discoveryExecutor.getThreadPoolExecutor().getQueue().isEmpty()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Background discovery did not finish in time");
    }

    private List<String> awaitComponents(long projectId, int expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(deadline)) {
            List<String> names = componentRepository.findByProjectIdOrderByNameAsc(projectId).stream().map(ProjectComponent::getName).toList();
            if (names.size() >= expected) {
                Thread.sleep(200);
                return componentRepository.findByProjectIdOrderByNameAsc(projectId).stream().map(ProjectComponent::getName).toList();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Components were not discovered in time");
    }
}

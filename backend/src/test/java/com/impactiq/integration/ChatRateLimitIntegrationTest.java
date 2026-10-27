package com.impactiq.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactiq.agent.AgentModelClient;
import com.impactiq.entity.AppUser;
import com.impactiq.entity.UserRole;
import com.impactiq.repository.AppUserRepository;
import com.impactiq.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Per-user message quota (database-backed, so it holds across instances) and concurrent-send protection. */
@SpringBootTest(properties = {
        TestProperties.NO_GEMINI_KEY,
        TestProperties.NO_AUTO_COMPONENTS,
        TestProperties.NO_EMBEDDINGS,
        TestProperties.STORAGE_DIR,
        TestProperties.NO_BOOTSTRAP_ADMIN,
        TestProperties.FAST_RETRIES,
        "impactiq.chat.rate-limit-per-minute=3"
})
@AutoConfigureTestDatabase
@AutoConfigureMockMvc
class ChatRateLimitIntegrationTest {

    private static final String REPLY = """
            {"answer":"No matching knowledge.","answerStatus":"NOT_FOUND_IN_KNOWLEDGE","citedEvidenceIds":[],
             "clarifyingQuestion":"","missingInformation":[],"followUpQuestions":[]}""";
    private static final long SLOW_MODEL_MS = 400;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AgentModelClient agentModelClient;

    @BeforeEach
    void setUp() {
        when(agentModelClient.engine()).thenReturn("TEST");
        when(agentModelClient.model()).thenReturn("scripted");
        when(agentModelClient.generateJson(any())).thenAnswer(invocation -> {
            Thread.sleep(SLOW_MODEL_MS);
            return REPLY;
        });
    }

    @Test
    void quotaIsEnforcedPerUserWithRetryAfter() throws Exception {
        AppUserPrincipal user = principal();
        AppUserPrincipal other = principal();
        long conversation = conversation(user);
        for (int index = 0; index < 3; index++) {
            assertEquals(200, send(user, conversation, "Question " + index).getStatus());
        }
        MockHttpServletResponse limited = send(user, conversation, "One too many");
        assertEquals(429, limited.getStatus());
        long retryAfter = Long.parseLong(limited.getHeader("Retry-After"));
        assertTrue(retryAfter >= 1 && retryAfter <= 61, "Retry-After is within the 60-second window");
        assertTrue(limited.getContentAsString().contains("3 per minute"));

        assertEquals(200, send(other, conversation(other), "Unaffected").getStatus(), "Quotas are per user");
    }

    @Test
    void concurrentSendsFromOneUserAreSerialised() throws Exception {
        AppUserPrincipal user = principal();
        long conversation = conversation(user);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int index = 0; index < 5; index++) {
                String content = "Parallel " + index;
                calls.add(() -> send(user, conversation, content).getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : pool.invokeAll(calls)) {
                statuses.add(future.get());
            }
            assertEquals(1, statuses.stream().filter(status -> status == 200).count(), "Only one reply runs at a time: " + statuses);
            assertTrue(statuses.stream().allMatch(status -> status == 200 || status == 429 || status == 503), statuses.toString());
        } finally {
            pool.shutdownNow();
        }
    }

    private AppUserPrincipal principal() {
        return principal(UserRole.USER);
    }

    private AppUserPrincipal principal(UserRole role) {
        AppUser user = new AppUser();
        user.setUsername("chat-rate-" + UUID.randomUUID().toString().substring(0, 8));
        user.setPasswordHash(passwordEncoder.encode("irrelevant-password"));
        user.setRole(role);
        return new AppUserPrincipal(userRepository.saveAndFlush(user));
    }

    private <T extends MockHttpServletRequestBuilder> T as(AppUserPrincipal user, T request) {
        request.with(authentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()))).with(csrf());
        return request;
    }

    /** Projects are created by an administrator; the regular user chats as a project member. */
    private long conversation(AppUserPrincipal user) throws Exception {
        AppUserPrincipal admin = principal(UserRole.ADMIN);
        String project = mockMvc.perform(as(admin, post("/api/projects")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Rate " + UUID.randomUUID() + "\"}")).andReturn().getResponse().getContentAsString();
        long projectId = objectMapper.readTree(project).path("id").asLong();
        mockMvc.perform(as(admin, post("/api/projects/" + projectId + "/members")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user.getUsername() + "\",\"role\":\"VIEWER\"}"));
        String created = mockMvc.perform(as(user, post("/api/chat/conversations")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"projectId\":" + projectId + "}")).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).path("conversation").path("id").asLong();
    }

    private MockHttpServletResponse send(AppUserPrincipal user, long conversationId, String content) throws Exception {
        return mockMvc.perform(as(user, post("/api/chat/conversations/" + conversationId + "/messages"))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("content", content))))
                .andReturn().getResponse();
    }
}

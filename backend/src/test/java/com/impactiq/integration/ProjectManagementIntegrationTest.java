package com.impactiq.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactiq.entity.AppUser;
import com.impactiq.entity.DocumentStatus;
import com.impactiq.entity.UserRole;
import com.impactiq.repository.AppUserRepository;
import com.impactiq.repository.ChatConversationRepository;
import com.impactiq.repository.DocumentChunkRepository;
import com.impactiq.repository.DocumentVersionRepository;
import com.impactiq.repository.KnowledgeDocumentRepository;
import com.impactiq.repository.ProjectComponentRepository;
import com.impactiq.repository.ProjectMemberRepository;
import com.impactiq.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Project rename and delete on H2: OWNER/admin only, name uniqueness, cascade of knowledge and chats, defect guard. */
@SpringBootTest(properties = {
        TestProperties.NO_GEMINI_KEY,
        TestProperties.NO_EMBEDDINGS,
        TestProperties.STORAGE_DIR,
        TestProperties.NO_BOOTSTRAP_ADMIN
})
@AutoConfigureTestDatabase
@AutoConfigureMockMvc
class ProjectManagementIntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private KnowledgeDocumentRepository documentRepository;
    @Autowired
    private DocumentVersionRepository versionRepository;
    @Autowired
    private DocumentChunkRepository chunkRepository;
    @Autowired
    private ProjectComponentRepository componentRepository;
    @Autowired
    private ProjectMemberRepository memberRepository;
    @Autowired
    private ChatConversationRepository conversationRepository;

    @Test
    void onlyAdminsManageProjectsWhileMembersKeepReadAccess() throws Exception {
        AppUserPrincipal admin = principal("pm-admin-" + suffix(), UserRole.ADMIN);
        AppUserPrincipal owner = principal("pm-owner-" + suffix(), UserRole.USER);
        AppUserPrincipal editor = principal("pm-editor-" + suffix(), UserRole.USER);
        AppUserPrincipal outsider = principal("pm-outsider-" + suffix(), UserRole.USER);
        mockMvc.perform(as(owner, post("/api/projects")).contentType(MediaType.APPLICATION_JSON)
                .content(json("User project " + suffix(), null))).andExpect(status().isForbidden());

        long projectId = createProject(admin, "Rename me " + suffix());
        String taken = "Taken " + suffix();
        createProject(admin, taken);
        addMember(admin, projectId, owner.getUsername(), "OWNER");
        addMember(admin, projectId, editor.getUsername(), "EDITOR");

        String newName = "Renamed " + suffix();
        mockMvc.perform(as(admin, put("/api/projects/" + projectId)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(newName, "  New description ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newName))
                .andExpect(jsonPath("$.description").value("New description"));
        updateRaw(admin, projectId, taken.toUpperCase(), null).andExpect(status().isConflict());
        updateRaw(admin, projectId, newName, null).andExpect(status().isOk());
        updateRaw(admin, projectId, "  ", null).andExpect(status().isBadRequest());
        updateRaw(owner, projectId, "Owner rename " + suffix(), null).andExpect(status().isForbidden());
        updateRaw(editor, projectId, "Editor rename " + suffix(), null).andExpect(status().isForbidden());
        updateRaw(outsider, projectId, "Outsider rename " + suffix(), null).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/projects/" + projectId).contentType(MediaType.APPLICATION_JSON)
                .content(json("No csrf", null)).with(authentication(token(admin)))).andExpect(status().isForbidden());

        // Members keep read access (projects list, chat), but project knowledge and membership are admin-only.
        mockMvc.perform(as(owner, get("/api/projects/" + projectId))).andExpect(status().isOk());
        mockMvc.perform(as(editor, get("/api/projects"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(projectId));
        mockMvc.perform(as(owner, get("/api/projects/" + projectId + "/documents"))).andExpect(status().isForbidden());
        mockMvc.perform(as(owner, get("/api/projects/" + projectId + "/members"))).andExpect(status().isForbidden());
        mockMvc.perform(as(editor, post("/api/projects/" + projectId + "/components")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"checkout\"}")).andExpect(status().isForbidden());
        postJson(owner, "/api/chat/conversations", "{\"projectId\":" + projectId + "}");

        // Admins see every registered account when adding members; regular users cannot list accounts.
        mockMvc.perform(as(admin, get("/api/users"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == '" + outsider.getUsername() + "')].role").value("USER"));
        mockMvc.perform(as(owner, get("/api/users"))).andExpect(status().isForbidden());
        // The administrator manages access, so even the only member OWNER can be demoted or removed.
        mockMvc.perform(as(admin, put("/api/projects/" + projectId + "/members/" + owner.id())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"VIEWER\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("VIEWER"));
    }

    @Test
    void deleteRemovesKnowledgeComponentsMembersAndChats() throws Exception {
        AppUserPrincipal admin = principal("pm-del-admin-" + suffix(), UserRole.ADMIN);
        AppUserPrincipal owner = principal("pm-del-" + suffix(), UserRole.USER);
        long projectId = createProject(admin, "Delete me " + suffix());
        long componentId = postJson(admin, "/api/projects/" + projectId + "/components", "{\"name\":\"checkout\"}").path("id").asLong();
        addMember(admin, projectId, owner.getUsername(), "OWNER");
        long documentId = uploadDocument(admin, projectId, componentId);
        awaitIndexed(documentId);
        postJson(owner, "/api/chat/conversations", "{\"projectId\":" + projectId + "}");
        assertTrue(chunkRepository.countByProjectId(projectId) > 0);

        mockMvc.perform(as(owner, delete("/api/projects/" + projectId))).andExpect(status().isForbidden());
        mockMvc.perform(as(admin, delete("/api/projects/" + projectId))).andExpect(status().isNoContent());

        mockMvc.perform(as(admin, get("/api/projects/" + projectId))).andExpect(status().isNotFound());
        mockMvc.perform(as(admin, delete("/api/projects/" + projectId))).andExpect(status().isNotFound());
        assertEquals(0, chunkRepository.countByProjectId(projectId));
        assertTrue(versionRepository.findByProjectId(projectId).isEmpty());
        assertTrue(documentRepository.findByProjectIdOrderByUpdatedAtDesc(projectId).isEmpty());
        assertTrue(componentRepository.findByProjectIdOrderByNameAsc(projectId).isEmpty());
        assertTrue(memberRepository.findByProjectIdOrderByCreatedAtAsc(projectId).isEmpty());
        assertTrue(conversationRepository.findIdsByProjectId(projectId).isEmpty());
    }

    @Test
    void deleteIsRefusedWhileDefectsBelongToTheProject() throws Exception {
        AppUserPrincipal admin = principal("pm-guard-" + suffix(), UserRole.ADMIN);
        long projectId = createProject(admin, "Has defects " + suffix());
        long defectId = postJson(admin, "/api/defects", """
                {"title":"Checkout fails","description":"500 on submit","environment":"PRODUCTION","service":"payments",
                 "affectedUsers":10,"frequency":"HIGH","businessCriticality":"HIGH","customerImpact":"HIGH",
                 "technicalImpact":"HIGH","projectId":%d}""".formatted(projectId)).path("id").asLong();

        mockMvc.perform(as(admin, delete("/api/projects/" + projectId))).andExpect(status().isConflict());
        mockMvc.perform(as(admin, get("/api/projects/" + projectId))).andExpect(status().isOk());

        mockMvc.perform(as(admin, delete("/api/defects/" + defectId))).andExpect(status().is2xxSuccessful());
        mockMvc.perform(as(admin, delete("/api/projects/" + projectId))).andExpect(status().isNoContent());
    }

    @Test
    void usersSeeOnlyTheDefectsTheyReportedWhileAdminsSeeAll() throws Exception {
        AppUserPrincipal admin = principal("pm-def-admin-" + suffix(), UserRole.ADMIN);
        AppUserPrincipal alice = principal("pm-def-alice-" + suffix(), UserRole.USER);
        AppUserPrincipal bob = principal("pm-def-bob-" + suffix(), UserRole.USER);
        String defect = """
                {"title":"%s","description":"500 on submit","environment":"PRODUCTION","service":"payments",
                 "affectedUsers":10,"frequency":"HIGH","businessCriticality":"HIGH","customerImpact":"HIGH",
                 "technicalImpact":"HIGH"}""";
        long aliceDefect = postJson(alice, "/api/defects", defect.formatted("Alice defect")).path("id").asLong();
        long bobDefect = postJson(bob, "/api/defects", defect.formatted("Bob defect")).path("id").asLong();

        mockMvc.perform(as(alice, get("/api/defects"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + aliceDefect + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + bobDefect + ")]").doesNotExist());
        mockMvc.perform(as(alice, get("/api/defects/" + bobDefect))).andExpect(status().isNotFound());
        mockMvc.perform(as(alice, delete("/api/defects/" + bobDefect))).andExpect(status().isNotFound());
        mockMvc.perform(as(admin, get("/api/defects"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + aliceDefect + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + bobDefect + ")]").exists());
        mockMvc.perform(as(admin, get("/api/defects/" + bobDefect))).andExpect(status().isOk());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private AppUserPrincipal principal(String username, UserRole role) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("unused-password"));
        user.setRole(role);
        return new AppUserPrincipal(userRepository.saveAndFlush(user));
    }

    private UsernamePasswordAuthenticationToken token(AppUserPrincipal user) {
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private <T extends MockHttpServletRequestBuilder> T as(AppUserPrincipal user, T request) {
        request.with(authentication(token(user))).with(csrf());
        return request;
    }

    private String json(String name, String description) throws Exception {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("name", name);
        payload.put("description", description);
        return objectMapper.writeValueAsString(payload);
    }

    private ResultActions updateRaw(AppUserPrincipal user, long projectId, String name, String description) throws Exception {
        return mockMvc.perform(as(user, put("/api/projects/" + projectId)).contentType(MediaType.APPLICATION_JSON)
                .content(json(name, description)));
    }

    private JsonNode postJson(AppUserPrincipal user, String url, String body) throws Exception {
        String response = mockMvc.perform(as(user, post(url)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private long createProject(AppUserPrincipal user, String name) throws Exception {
        return postJson(user, "/api/projects", json(name, null)).path("id").asLong();
    }

    private void addMember(AppUserPrincipal owner, long projectId, String username, String role) throws Exception {
        postJson(owner, "/api/projects/" + projectId + "/members",
                "{\"username\":\"" + username + "\",\"role\":\"" + role + "\"}");
    }

    private long uploadDocument(AppUserPrincipal user, long projectId, long componentId) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/projects/" + projectId + "/documents");
        request.file(new MockMultipartFile("file", "runbook.md", "text/plain",
                "# Runbook\nPaymentService.charge needs a currency code.\n".getBytes(StandardCharsets.UTF_8)));
        request.param("componentId", String.valueOf(componentId));
        String body = mockMvc.perform(as(user, request)).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("id").asLong();
    }

    private void awaitIndexed(long documentId) throws InterruptedException {
        Instant deadline = Instant.now().plus(WAIT);
        while (Instant.now().isBefore(deadline)) {
            var version = versionRepository.findByDocumentIdAndVersionNumber(documentId, 1);
            if (version.isPresent() && Set.of(DocumentStatus.INDEXED, DocumentStatus.FAILED).contains(version.get().getStatus())) {
                assertEquals(DocumentStatus.INDEXED, version.get().getStatus(), version.get().getErrorMessage());
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Document was not indexed in time");
    }
}

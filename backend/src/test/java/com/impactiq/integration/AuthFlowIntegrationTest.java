package com.impactiq.integration;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real session + CSRF flow through the security filter chain (H2 database, no model calls). */
@SpringBootTest(properties = {
        TestProperties.NO_GEMINI_KEY,
        TestProperties.NO_EMBEDDINGS,
        TestProperties.STORAGE_DIR,
        TestProperties.NO_BOOTSTRAP_ADMIN
})
@AutoConfigureTestDatabase
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedEndpointsRequireAuthenticationAndCsrf() throws Exception {
        mockMvc.perform(get("/api/defects")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());

        // State-changing request without a CSRF token is rejected even before authentication.
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nocsrf\",\"password\":\"password-123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registerLoginAndUseSession() throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        String token = csrfCookie.getValue();

        mockMvc.perform(post("/api/auth/register").cookie(csrfCookie).header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"correct-horse-1\",\"displayName\":\"Alice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"));

        mockMvc.perform(post("/api/auth/login").cookie(csrfCookie).header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());

        MvcResult login = mockMvc.perform(post("/api/auth/login").cookie(csrfCookie).header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"correct-horse-1\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.username").value("alice"));
        mockMvc.perform(get("/api/defects").session(session)).andExpect(status().isOk());
    }

    @Test
    void repeatedFailedLoginsLockTheAccount() throws Exception {
        MvcResult csrf = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        Cookie csrfCookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/auth/login").cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"mallory\",\"password\":\"guess-" + attempt + "\"}"));
        }
        mockMvc.perform(post("/api/auth/login").cookie(csrfCookie).header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"mallory\",\"password\":\"guess-final\"}"))
                .andExpect(status().isTooManyRequests());
    }
}

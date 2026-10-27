package com.impactiq.controller;

import com.impactiq.dto.DefectRequest;
import com.impactiq.dto.DefectResponse;
import com.impactiq.entity.BusinessCriticality;
import com.impactiq.entity.CustomerImpact;
import com.impactiq.entity.DefectStatus;
import com.impactiq.entity.Environment;
import com.impactiq.entity.Frequency;
import com.impactiq.entity.TechnicalImpact;
import com.impactiq.exception.DefectNotFoundException;
import com.impactiq.exception.GlobalExceptionHandler;
import com.impactiq.service.DefectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DefectController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DefectControllerTest {

    private static final String REQUEST_BODY = """
            {
              "title": "Checkout fails",
              "description": "Payment submission returns an error",
              "environment": "PRODUCTION",
              "service": "checkout-api",
              "businessFunction": "Payments",
              "affectedUsers": 125,
              "frequency": "HIGH",
              "businessCriticality": "CRITICAL",
              "customerImpact": "HIGH",
              "technicalImpact": "MEDIUM"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DefectService defectService;

    private final DefectResponse defectResponse = new DefectResponse(
            41L,
            "Checkout fails",
            "Payment submission returns an error",
            Environment.PRODUCTION,
            "checkout-api",
            "Payments",
            125,
            Frequency.HIGH,
            BusinessCriticality.CRITICAL,
            CustomerImpact.HIGH,
            TechnicalImpact.MEDIUM,
            DefectStatus.OPEN,
            Instant.parse("2026-10-07T00:00:00Z"),
            Instant.parse("2026-10-07T00:00:00Z"),
            79,
            "HIGH",
            "P1",
            null,
            null,
            null,
            null,
            null
    );

    @Test
    void createReturnsCreatedAndLocation() throws Exception {
        when(defectService.create(any(DefectRequest.class))).thenReturn(defectResponse);

        mockMvc.perform(post("/api/defects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/defects/41")))
                .andExpect(jsonPath("$.id").value(41))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void listReturnsDefects() throws Exception {
        when(defectService.findAll()).thenReturn(List.of(defectResponse));

        mockMvc.perform(get("/api/defects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(41))
                .andExpect(jsonPath("$[0].title").value("Checkout fails"));
    }

    @Test
    void getByIdReturnsDefect() throws Exception {
        when(defectService.findById(41L)).thenReturn(defectResponse);

        mockMvc.perform(get("/api/defects/41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(41))
                .andExpect(jsonPath("$.environment").value("PRODUCTION"));
    }

    @Test
    void updateReturnsUpdatedDefect() throws Exception {
        when(defectService.update(org.mockito.ArgumentMatchers.eq(41L), any(DefectRequest.class)))
                .thenReturn(defectResponse);

        mockMvc.perform(put("/api/defects/41")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(41));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/defects/41"))
                .andExpect(status().isNoContent());
    }

    @Test
    void blankTitleAndDescriptionAreRejected() throws Exception {
        mockMvc.perform(post("/api/defects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":" ","description":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.title").exists())
                .andExpect(jsonPath("$.validationErrors.description").exists());
    }

    @Test
    void overlongTextFieldsAreRejectedWithFieldMessages() throws Exception {
        String body = """
                {"title":"%s","description":"%s","service":"%s","businessFunction":"%s"}
                """.formatted("t".repeat(256), "d".repeat(20_001), "s".repeat(256), "b".repeat(256));

        mockMvc.perform(post("/api/defects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.title").value("Title must be at most 255 characters"))
                .andExpect(jsonPath("$.validationErrors.description").value("Description must be at most 20000 characters"))
                .andExpect(jsonPath("$.validationErrors.service").value("Service must be at most 255 characters"))
                .andExpect(jsonPath("$.validationErrors.businessFunction").value("Business function must be at most 255 characters"));
        verify(defectService, never()).create(any(DefectRequest.class));
    }

    @Test
    void textFieldsAtMaximumLengthAreAccepted() throws Exception {
        when(defectService.create(any(DefectRequest.class))).thenReturn(defectResponse);
        String body = """
                {"title":"%s","description":"%s","service":"%s","businessFunction":"%s"}
                """.formatted("t".repeat(255), "d".repeat(20_000), "s".repeat(255), "b".repeat(255));

        mockMvc.perform(post("/api/defects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void responseExposesDeterministicBaseline() throws Exception {
        when(defectService.findById(41L)).thenReturn(defectResponse);

        mockMvc.perform(get("/api/defects/41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineScore").value(79))
                .andExpect(jsonPath("$.baselineRiskLevel").value("HIGH"))
                .andExpect(jsonPath("$.baselinePriority").value("P1"));
    }

    @Test
    void negativeAffectedUsersAreRejected() throws Exception {
        mockMvc.perform(post("/api/defects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Issue","description":"Details","affectedUsers":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.affectedUsers").exists());
    }

    @Test
    void missingDefectReturnsNotFound() throws Exception {
        when(defectService.findById(404L)).thenThrow(new DefectNotFoundException(404L));

        mockMvc.perform(get("/api/defects/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}

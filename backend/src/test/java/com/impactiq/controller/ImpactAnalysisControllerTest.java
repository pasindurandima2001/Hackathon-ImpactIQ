package com.impactiq.controller;

import com.impactiq.dto.ImpactAnalysisResponse;
import com.impactiq.dto.ImpactScoreBreakdown;
import com.impactiq.entity.ImpactPriority;
import com.impactiq.entity.ImpactRiskLevel;
import com.impactiq.exception.GeminiServiceException;
import com.impactiq.exception.GlobalExceptionHandler;
import com.impactiq.exception.ImpactAnalysisException;
import com.impactiq.exception.ImpactAnalysisNotFoundException;
import com.impactiq.service.ImpactAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImpactAnalysisController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ImpactAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImpactAnalysisService impactAnalysisService;

    @MockitoBean
    private com.impactiq.service.DefectService defectService;

    private ImpactAnalysisResponse sampleResponse() {
        return new ImpactAnalysisResponse(
                12L,
                7L,
                84,
                ImpactRiskLevel.CRITICAL,
                ImpactPriority.P0,
                92,
                "Checkout payments are failing for production users.",
                "The defect affects the production checkout service and the supplied payment business function.",
                List.of("Restore payment submission and verify transactions."),
                List.of("checkout-api", "Payments"),
                76,
                new ImpactScoreBreakdown(75, 100, 75, 75),
                Instant.parse("2026-10-07T08:00:00Z")
        );
    }

    @Test
    void analyzeReturnsCreatedAnalysisDto() throws Exception {
        when(impactAnalysisService.analyze(7L)).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/defects/7/analyze"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/defects/7/analysis"))
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.defectId").value(7))
                .andExpect(jsonPath("$.baselineScore").value(76))
                .andExpect(jsonPath("$.scoreBreakdown.userImpactScore").value(75))
                .andExpect(jsonPath("$.recommendedActions[0]").exists());
    }

    @Test
    void reanalyzeReturnsNewAnalysisDto() throws Exception {
        when(impactAnalysisService.reanalyze(7L)).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/defects/7/reanalyze"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.impactScore").value(84));
    }

    @Test
    void latestAnalysisReturnsDto() throws Exception {
        when(impactAnalysisService.getLatest(7L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/defects/7/analysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("CRITICAL"))
                .andExpect(jsonPath("$.priority").value("P0"));
    }

    @Test
    void historyReturnsNewestFirstArray() throws Exception {
        when(impactAnalysisService.getHistory(7L)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/defects/7/analysis/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(12))
                .andExpect(jsonPath("$[0].defectId").value(7));
    }

    @Test
    void missingDefectReturnsNotFound() throws Exception {
        when(impactAnalysisService.analyze(999L)).thenThrow(new com.impactiq.exception.DefectNotFoundException(999L));

        mockMvc.perform(post("/api/defects/999/analyze"))
                .andExpect(status().isNotFound());
    }

    @Test
    void noLatestAnalysisReturnsNotFound() throws Exception {
        when(impactAnalysisService.getLatest(7L)).thenThrow(new ImpactAnalysisNotFoundException(7L));

        mockMvc.perform(get("/api/defects/7/analysis"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unavailableGeminiReturnsServiceUnavailable() throws Exception {
        when(impactAnalysisService.analyze(7L)).thenThrow(new GeminiServiceException.MissingApiKey());

        mockMvc.perform(post("/api/defects/7/analyze"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void overloadedGeminiReturnsRetryableServiceUnavailable() throws Exception {
        when(impactAnalysisService.analyze(7L)).thenThrow(new GeminiServiceException.ApiFailure(503));

        mockMvc.perform(post("/api/defects/7/analyze"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Gemini is temporarily overloaded. Retry shortly."));
    }

    @Test
    void invalidGeminiResponseReturnsBadGateway() throws Exception {
        when(impactAnalysisService.analyze(7L)).thenThrow(new ImpactAnalysisException.InvalidModelResponse());

        mockMvc.perform(post("/api/defects/7/analyze"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void databaseFailureReturnsSanitizedServerError() throws Exception {
        when(impactAnalysisService.analyze(7L)).thenThrow(new DataAccessResourceFailureException("sensitive database details"));

        mockMvc.perform(post("/api/defects/7/analyze"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unable to complete the database operation"));
    }
}

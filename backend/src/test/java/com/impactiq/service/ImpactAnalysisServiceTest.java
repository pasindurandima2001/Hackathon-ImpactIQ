package com.impactiq.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactiq.dto.GeminiImpactAnalysisResponse;
import com.impactiq.entity.BusinessCriticality;
import com.impactiq.entity.CustomerImpact;
import com.impactiq.entity.Defect;
import com.impactiq.entity.Environment;
import com.impactiq.entity.Frequency;
import com.impactiq.entity.ImpactAnalysis;
import com.impactiq.entity.ImpactPriority;
import com.impactiq.entity.ImpactRiskLevel;
import com.impactiq.entity.TechnicalImpact;
import com.impactiq.exception.ImpactAnalysisException;
import com.impactiq.exception.ImpactAnalysisNotFoundException;
import com.impactiq.exception.GeminiServiceException;
import com.impactiq.repository.DefectRepository;
import com.impactiq.repository.ImpactAnalysisRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImpactAnalysisServiceTest {

    private static final String VALID_RESPONSE = """
            {
              "impactScore": 86,
              "riskLevel": "CRITICAL",
              "priority": "P0",
              "confidence": 91,
              "summary": "Production checkout failures disrupt payment completion.",
              "explanation": "The defect is in production and affects a critical payment function. The supplied report identifies 1,500 affected users; no additional customer or revenue facts are assumed.",
              "recommendedActions": ["Prioritize restoring checkout and verify successful payment submissions."],
              "affectedAreas": ["checkout-api", "Payments"]
            }
            """;

    private DefectRepository defectRepository;
    private ImpactAnalysisRepository impactAnalysisRepository;
    private GeminiService geminiService;
    private ImpactAnalysisService impactAnalysisService;

    @BeforeEach
    void setUp() {
        defectRepository = mock(DefectRepository.class);
        impactAnalysisRepository = mock(ImpactAnalysisRepository.class);
        geminiService = mock(GeminiService.class);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        impactAnalysisService = new ImpactAnalysisService(
                defectRepository,
                impactAnalysisRepository,
                new ImpactScoringService(new com.impactiq.config.AffectedUsersScoringProperties()),
                geminiService,
                new ObjectMapper(),
                validator
        );
    }

    @Test
        void validAnalysisIsValidatedThenSavedWithBaselineAndArrays() {
        Defect defect = sampleDefect();
        when(defectRepository.findById(73L)).thenReturn(Optional.of(defect));
        when(geminiService.sendStructuredPrompt(anyString(), anyString(), any())).thenReturn(VALID_RESPONSE);
        when(impactAnalysisRepository.saveAndFlush(any(ImpactAnalysis.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GeminiImpactAnalysisResponse result = impactAnalysisService.analyzeDefect(73L);

        assertEquals(86, result.impactScore());
        assertEquals(ImpactRiskLevel.CRITICAL, result.riskLevel());
        assertEquals(ImpactPriority.P0, result.priority());
        assertEquals(91, result.confidence());
        ArgumentCaptor<ImpactAnalysis> savedAnalysis = ArgumentCaptor.forClass(ImpactAnalysis.class);
        verify(impactAnalysisRepository).saveAndFlush(savedAnalysis.capture());
        assertEquals(73L, savedAnalysis.getValue().getDefectId());
        assertEquals(85, savedAnalysis.getValue().getBaselineScore());
        assertEquals(100, savedAnalysis.getValue().getUserImpactScore());
        assertEquals(100, savedAnalysis.getValue().getBusinessImpactScore());
        assertEquals(75, savedAnalysis.getValue().getCustomerImpactScore());
        assertEquals(50, savedAnalysis.getValue().getTechnicalImpactScore());
        assertEquals(List.of("checkout-api", "Payments"), savedAnalysis.getValue().getAffectedAreas());
        assertEquals(1, savedAnalysis.getValue().getRecommendedActions().size());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(geminiService).sendStructuredPrompt(anyString(), prompt.capture(), any());
        assertTrue(prompt.getValue().contains("Production checkout failures"));
        assertTrue(prompt.getValue().contains("\"baselineImpactScore\":85"));
        assertTrue(prompt.getValue().contains("\"userImpactScore\":100"));
        assertEquals("Production checkout failures disrupt payment completion.", result.summary());
    }

    @Test
    void invalidEnumResponseIsRejectedAndNeverSaved() {
        when(defectRepository.findById(73L)).thenReturn(Optional.of(sampleDefect()));
        when(geminiService.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(VALID_RESPONSE.replace("\"priority\": \"P0\"", "\"priority\": \"P9\""));

        assertThrows(ImpactAnalysisException.InvalidModelResponse.class,
                () -> impactAnalysisService.analyzeDefect(73L));
        verify(impactAnalysisRepository, never()).saveAndFlush(any(ImpactAnalysis.class));
    }

    @Test
    void missingRequiredFieldIsRejectedAndNeverSaved() {
        when(defectRepository.findById(73L)).thenReturn(Optional.of(sampleDefect()));
        when(geminiService.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenReturn(VALID_RESPONSE.replaceAll(
                        ",\\s*\"affectedAreas\"\\s*:\\s*\\[\"checkout-api\",\\s*\"Payments\"\\]", ""));

        assertThrows(ImpactAnalysisException.InvalidModelResponse.class,
                () -> impactAnalysisService.analyzeDefect(73L));
        verify(impactAnalysisRepository, never()).saveAndFlush(any(ImpactAnalysis.class));
    }

        @Test
        void outOfRangeScoreIsRejectedAndNeverSaved() {
                when(defectRepository.findById(73L)).thenReturn(Optional.of(sampleDefect()));
                when(geminiService.sendStructuredPrompt(anyString(), anyString(), any()))
                                .thenReturn(VALID_RESPONSE.replace("\"impactScore\": 86", "\"impactScore\": 101"));

                assertThrows(ImpactAnalysisException.InvalidModelResponse.class,
                                () -> impactAnalysisService.analyzeDefect(73L));
                verify(impactAnalysisRepository, never()).saveAndFlush(any(ImpactAnalysis.class));
        }

    @Test
    void missingDefectFailsBeforeGeminiIsCalled() {
        when(defectRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(com.impactiq.exception.DefectNotFoundException.class,
                () -> impactAnalysisService.analyzeDefect(404L));
        verify(geminiService, never()).sendStructuredPrompt(anyString(), anyString(), any());
        verify(impactAnalysisRepository, never()).saveAndFlush(any(ImpactAnalysis.class));
    }

    @Test
    void GeminiFailureIsPropagatedAndAnalysisIsNotSaved() {
        when(defectRepository.findById(73L)).thenReturn(Optional.of(sampleDefect()));
        when(geminiService.sendStructuredPrompt(anyString(), anyString(), any()))
                .thenThrow(new GeminiServiceException.MissingApiKey());

        assertThrows(GeminiServiceException.MissingApiKey.class,
                () -> impactAnalysisService.analyzeDefect(73L));
        verify(impactAnalysisRepository, never()).saveAndFlush(any(ImpactAnalysis.class));
    }

        @Test
        void latestAnalysisMapsPersistedRecordToResponseDto() {
                when(defectRepository.existsById(73L)).thenReturn(true);
                when(impactAnalysisRepository.findByDefectIdOrderByCreatedAtDescIdDesc(73L))
                                .thenReturn(List.of(persistedAnalysis(12L, 84, 76)));

                var result = impactAnalysisService.getLatest(73L);

                assertEquals(12L, result.id());
                assertEquals(84, result.impactScore());
                assertEquals(76, result.baselineScore());
                assertEquals(List.of("Payments"), result.affectedAreas());
        }

        @Test
        void missingLatestAnalysisRaisesNotFound() {
                when(defectRepository.existsById(73L)).thenReturn(true);
                when(impactAnalysisRepository.findByDefectIdOrderByCreatedAtDescIdDesc(73L)).thenReturn(List.of());

                assertThrows(ImpactAnalysisNotFoundException.class, () -> impactAnalysisService.getLatest(73L));
        }

        @Test
        void historyMapsRepositoryResultsInNewestFirstOrder() {
                ImpactAnalysis older = persistedAnalysis(10L, 72, 76);
                ReflectionTestUtils.setField(older, "createdAt", Instant.parse("2026-10-07T07:00:00Z"));
                ImpactAnalysis newer = persistedAnalysis(11L, 84, 76);
                ReflectionTestUtils.setField(newer, "createdAt", Instant.parse("2026-10-07T08:00:00Z"));
                when(defectRepository.existsById(73L)).thenReturn(true);
                when(impactAnalysisRepository.findByDefectIdOrderByCreatedAtDescIdDesc(73L))
                                .thenReturn(List.of(older, newer));

                var result = impactAnalysisService.getHistory(73L);

                assertEquals(List.of(11L, 10L), result.stream().map(item -> item.id()).toList());
        }

        private ImpactAnalysis persistedAnalysis(Long id, int impactScore, int baselineScore) {
                ImpactAnalysis analysis = new ImpactAnalysis();
                ReflectionTestUtils.setField(analysis, "id", id);
                analysis.setDefectId(73L);
                analysis.setImpactScore(impactScore);
                analysis.setRiskLevel(ImpactRiskLevel.CRITICAL);
                analysis.setPriority(ImpactPriority.P0);
                analysis.setConfidence(90);
                analysis.setSummary("Payment impact analysis");
                analysis.setExplanation("Analysis from supplied defect information.");
                analysis.setRecommendedActions(List.of("Restore payment processing."));
                analysis.setAffectedAreas(List.of("Payments"));
                analysis.setBaselineScore(baselineScore);
                analysis.setUserImpactScore(75);
                analysis.setBusinessImpactScore(100);
                analysis.setCustomerImpactScore(75);
                analysis.setTechnicalImpactScore(75);
                return analysis;
        }

    private Defect sampleDefect() {
        Defect defect = new Defect();
        defect.setTitle("Production checkout failures");
        defect.setDescription("Payment submissions fail for some users after the latest release.");
        defect.setEnvironment(Environment.PRODUCTION);
        defect.setService("checkout-api");
        defect.setBusinessFunction("Payments");
        defect.setAffectedUsers(1500);
        defect.setFrequency(Frequency.HIGH);
        defect.setBusinessCriticality(BusinessCriticality.CRITICAL);
        defect.setCustomerImpact(CustomerImpact.HIGH);
        defect.setTechnicalImpact(TechnicalImpact.MEDIUM);
        return defect;
    }
}

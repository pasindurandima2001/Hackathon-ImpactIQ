package com.impactiq.service;

import com.impactiq.dto.DefectRequest;
import com.impactiq.dto.GeminiImpactAnalysisResponse;
import com.impactiq.entity.BusinessCriticality;
import com.impactiq.entity.CustomerImpact;
import com.impactiq.entity.DefectStatus;
import com.impactiq.entity.Environment;
import com.impactiq.entity.Frequency;
import com.impactiq.entity.ImpactAnalysis;
import com.impactiq.entity.TechnicalImpact;
import com.impactiq.repository.ImpactAnalysisRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GeminiImpactAnalysisIntegrationTest {

    @Autowired
    private DefectService defectService;

    @Autowired
    private ImpactAnalysisService impactAnalysisService;

    @Autowired
    private ImpactAnalysisRepository impactAnalysisRepository;

    @Test
    void createsDefectAnalyzesItAndPersistsTheAnalysis() {
        DefectRequest request = new DefectRequest(
                "Checkout payment submission fails",
                "Production payment submissions intermittently return an error after the recent release.",
                Environment.PRODUCTION,
                "checkout-api",
                "Payments",
                120,
                Frequency.HIGH,
                BusinessCriticality.CRITICAL,
                CustomerImpact.HIGH,
                TechnicalImpact.HIGH,
                DefectStatus.OPEN
        );
        Long defectId = defectService.create(request).id();

        GeminiImpactAnalysisResponse response = impactAnalysisService.analyzeDefect(defectId);

        assertNotNull(response);
        assertFalse(response.summary().isBlank());
        ImpactAnalysis savedAnalysis = impactAnalysisRepository.findByDefectIdOrderByCreatedAtDescIdDesc(defectId)
                .stream()
                .findFirst()
                .orElseThrow();
        assertNotNull(savedAnalysis.getId());
        assertNotNull(savedAnalysis.getCreatedAt());
    }
}

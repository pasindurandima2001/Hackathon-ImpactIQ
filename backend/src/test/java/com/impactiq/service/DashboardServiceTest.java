package com.impactiq.service;

import com.impactiq.config.AffectedUsersScoringProperties;
import com.impactiq.entity.BusinessCriticality;
import com.impactiq.entity.CustomerImpact;
import com.impactiq.entity.Defect;
import com.impactiq.entity.Environment;
import com.impactiq.entity.Frequency;
import com.impactiq.entity.ImpactAnalysis;
import com.impactiq.entity.ImpactPriority;
import com.impactiq.entity.ImpactRiskLevel;
import com.impactiq.entity.TechnicalImpact;
import com.impactiq.repository.DefectRepository;
import com.impactiq.repository.ImpactAnalysisRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    @Test
    void metricsUseDeterministicScoreAndReportAiScoreSeparately() {
        Defect analyzed = defect(1L, "Checkout", Environment.PRODUCTION, 120,
                Frequency.HIGH, BusinessCriticality.CRITICAL, CustomerImpact.HIGH, TechnicalImpact.HIGH);
        Defect baselineOnly = defect(2L, "Catalog", Environment.STAGING, 10,
                Frequency.LOW, BusinessCriticality.LOW, CustomerImpact.LOW, TechnicalImpact.LOW);

        // The AI suggestion deliberately disagrees with the deterministic CRITICAL baseline (83).
        ImpactAnalysis analysis = new ImpactAnalysis();
        analysis.setDefectId(1L);
        analysis.setImpactScore(40);
        analysis.setRiskLevel(ImpactRiskLevel.MEDIUM);
        analysis.setPriority(ImpactPriority.P2);
        analysis.setConfidence(95);
        analysis.setSummary("Checkout impact");
        analysis.setExplanation("Supplied defect facts only.");
        analysis.setRecommendedActions(List.of("Restore checkout."));
        analysis.setAffectedAreas(List.of("checkout-api"));
        analysis.setBaselineScore(81);
        analysis.setUserImpactScore(75);
        analysis.setBusinessImpactScore(100);
        analysis.setCustomerImpactScore(75);
        analysis.setTechnicalImpactScore(75);
        org.springframework.test.util.ReflectionTestUtils.setField(analysis, "id", 10L);
        org.springframework.test.util.ReflectionTestUtils.setField(analysis, "createdAt", Instant.parse("2026-10-07T10:00:00Z"));

        ImpactAnalysisRepository analyses = mock(ImpactAnalysisRepository.class);
        when(analyses.findAll()).thenReturn(List.of(analysis));
        InvestigationMetricsService investigationMetrics = mock(InvestigationMetricsService.class);

        DashboardMetricsResponseAssert result = new DashboardMetricsResponseAssert(
                new DashboardService(mock(DefectService.class), mock(com.impactiq.security.CurrentUserService.class), analyses,
                        new ImpactScoringService(new AffectedUsersScoringProperties()), investigationMetrics)
                        .getMetrics(List.of(analyzed, baselineOnly)));

        assertEquals(2, result.totalDefects());
        assertEquals(1, result.criticalDefects());
        assertEquals(0, result.mediumDefects());
        assertEquals(1, result.lowDefects());
        assertEquals(54, result.averageImpactScore());
        assertEquals(83, result.topScore());
        assertEquals("CRITICAL", result.top().riskLevel());
        assertEquals("P0", result.top().priority());
        assertEquals(40, result.top().aiImpactScore());
        assertEquals("Checkout issue", result.topTitle());
        assertNull(result.response().topImpactDefects().get(1).aiImpactScore());
    }

    private Defect defect(Long id, String service, Environment environment, int users,
                          Frequency frequency, BusinessCriticality business, CustomerImpact customer,
                          TechnicalImpact technical) {
        Defect defect = new Defect();
        org.springframework.test.util.ReflectionTestUtils.setField(defect, "id", id);
        defect.setTitle(service + " issue");
        defect.setDescription("Supplied defect description.");
        defect.setEnvironment(environment);
        defect.setService(service);
        defect.setAffectedUsers(users);
        defect.setFrequency(frequency);
        defect.setBusinessCriticality(business);
        defect.setCustomerImpact(customer);
        defect.setTechnicalImpact(technical);
        return defect;
    }

    private record DashboardMetricsResponseAssert(com.impactiq.dto.DashboardMetricsResponse response) {
        long totalDefects() { return response.totalDefects(); }
        long criticalDefects() { return response.criticalDefects(); }
        long mediumDefects() { return response.mediumDefects(); }
        long lowDefects() { return response.lowDefects(); }
        int averageImpactScore() { return response.averageImpactScore(); }
        com.impactiq.dto.DashboardDefectSummary top() { return response.topImpactDefects().get(0); }
        int topScore() { return response.topImpactDefects().get(0).impactScore(); }
        String topTitle() { return response.topImpactDefects().get(0).title(); }
    }
}

package com.impactiq.service;

import com.impactiq.config.AffectedUsersScoringProperties;
import com.impactiq.dto.ImpactScoreResult;
import com.impactiq.entity.BusinessCriticality;
import com.impactiq.entity.CustomerImpact;
import com.impactiq.entity.Defect;
import com.impactiq.entity.TechnicalImpact;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImpactScoringServiceTest {

    private ImpactScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService = new ImpactScoringService(new AffectedUsersScoringProperties());
    }

    @Test
    void lowImpactDefectProducesLowRisk() {
        ImpactScoreResult result = scoringService.calculate(defect(ImpactBand.LOW, 10));

        assertEquals(new ImpactScoreResult(25, 25, 25, 25, 25, "LOW", "P3"), result);
    }

    @Test
    void mediumImpactDefectProducesMediumRisk() {
        ImpactScoreResult result = scoringService.calculate(defect(ImpactBand.MEDIUM, 11));

        assertEquals(new ImpactScoreResult(50, 50, 50, 50, 50, "MEDIUM", "P2"), result);
    }

    @Test
    void highImpactDefectProducesHighRisk() {
        ImpactScoreResult result = scoringService.calculate(defect(ImpactBand.HIGH, 101));

        assertEquals(new ImpactScoreResult(75, 75, 75, 75, 75, "HIGH", "P1"), result);
    }

    @Test
    void criticalImpactDefectProducesCriticalRisk() {
        ImpactScoreResult result = scoringService.calculate(defect(ImpactBand.CRITICAL, 1001));

        assertEquals(new ImpactScoreResult(100, 100, 100, 100, 100, "CRITICAL", "P0"), result);
    }

    @Test
    void affectedUserBandsCanBeConfigured() {
        AffectedUsersScoringProperties customRanges = new AffectedUsersScoringProperties();
        customRanges.setLowMax(2);
        customRanges.setMediumMax(5);
        customRanges.setHighMax(8);
        ImpactScoringService customScoringService = new ImpactScoringService(customRanges);

        ImpactScoreResult result = customScoringService.calculate(defect(ImpactBand.LOW, 3));

        assertEquals(50, result.userImpactScore());
    }

    private Defect defect(ImpactBand band, int affectedUsers) {
        Defect defect = new Defect();
        defect.setAffectedUsers(affectedUsers);
        defect.setBusinessCriticality(switch (band) {
            case LOW -> BusinessCriticality.LOW;
            case MEDIUM -> BusinessCriticality.MEDIUM;
            case HIGH -> BusinessCriticality.HIGH;
            case CRITICAL -> BusinessCriticality.CRITICAL;
        });
        defect.setCustomerImpact(switch (band) {
            case LOW -> CustomerImpact.LOW;
            case MEDIUM -> CustomerImpact.MEDIUM;
            case HIGH -> CustomerImpact.HIGH;
            case CRITICAL -> CustomerImpact.CRITICAL;
        });
        defect.setTechnicalImpact(switch (band) {
            case LOW -> TechnicalImpact.LOW;
            case MEDIUM -> TechnicalImpact.MEDIUM;
            case HIGH -> TechnicalImpact.HIGH;
            case CRITICAL -> TechnicalImpact.CRITICAL;
        });
        return defect;
    }

    private enum ImpactBand {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
}

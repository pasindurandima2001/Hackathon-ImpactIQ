package com.impactiq.dto;

public record ImpactScoreResult(
        int totalScore,
        int userImpactScore,
        int businessImpactScore,
        int customerImpactScore,
        int technicalImpactScore,
        String riskLevel,
        String priority
) {
}

package com.impactiq.dto;

public record ImpactScoreBreakdown(
        int userImpactScore,
        int businessImpactScore,
        int customerImpactScore,
        int technicalImpactScore
) {
}

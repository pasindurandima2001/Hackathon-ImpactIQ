package com.impactiq.dto;

import com.impactiq.entity.ImpactPriority;
import com.impactiq.entity.ImpactRiskLevel;

import java.time.Instant;
import java.util.List;

public record ImpactAnalysisResponse(
        Long id,
        Long defectId,
        int impactScore,
        ImpactRiskLevel riskLevel,
        ImpactPriority priority,
        int confidence,
        String summary,
        String explanation,
        List<String> recommendedActions,
        List<String> affectedAreas,
        int baselineScore,
        ImpactScoreBreakdown scoreBreakdown,
        Instant createdAt
) {
}

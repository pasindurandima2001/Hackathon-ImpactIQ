package com.impactiq.dto;

import com.impactiq.entity.BusinessCriticality;
import com.impactiq.entity.CustomerImpact;
import com.impactiq.entity.DefectStatus;
import com.impactiq.entity.Environment;
import com.impactiq.entity.Frequency;
import com.impactiq.entity.TechnicalImpact;

import java.time.Instant;

public record DefectResponse(
        Long id,
        String title,
        String description,
        Environment environment,
        String service,
        String businessFunction,
        Integer affectedUsers,
        Frequency frequency,
        BusinessCriticality businessCriticality,
        CustomerImpact customerImpact,
        TechnicalImpact technicalImpact,
        DefectStatus status,
        Instant createdAt,
        Instant updatedAt,
        int baselineScore,
        String baselineRiskLevel,
        String baselinePriority,
        Long projectId,
        String projectName,
        Long componentId,
        String componentName,
        ImpactScoreBreakdown baselineBreakdown
) {
}

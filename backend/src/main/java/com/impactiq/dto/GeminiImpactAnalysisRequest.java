package com.impactiq.dto;

import com.impactiq.entity.BusinessCriticality;
import com.impactiq.entity.CustomerImpact;
import com.impactiq.entity.Environment;
import com.impactiq.entity.TechnicalImpact;

public record GeminiImpactAnalysisRequest(
        String title,
        String description,
        Environment environment,
        String service,
        String businessFunction,
        Integer affectedUsers,
        BusinessCriticality businessCriticality,
        CustomerImpact customerImpact,
        TechnicalImpact technicalImpact,
        int baselineImpactScore,
        String baselineRiskLevel,
        String baselinePriority,
        int userImpactScore,
        int businessImpactScore,
        int customerImpactScore,
        int technicalImpactScore
) {
}

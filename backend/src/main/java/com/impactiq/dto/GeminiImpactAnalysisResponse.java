package com.impactiq.dto;

import com.impactiq.entity.ImpactPriority;
import com.impactiq.entity.ImpactRiskLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record GeminiImpactAnalysisResponse(
        @Min(0) @Max(100)
        int impactScore,
        @NotNull
        ImpactRiskLevel riskLevel,
        @NotNull
        ImpactPriority priority,
        @Min(0) @Max(100)
        int confidence,
        @NotBlank @Size(max = 500)
        String summary,
        @NotBlank @Size(max = 10000)
        String explanation,
        @NotEmpty @Size(max = 20)
        List<@NotBlank @Size(max = 1000) String> recommendedActions,
        @NotEmpty @Size(max = 20)
        List<@NotBlank @Size(max = 255) String> affectedAreas
) {
}

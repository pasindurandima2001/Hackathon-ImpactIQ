package com.impactiq.agent;

/**
 * Defect facts given to agents. Baseline fields come from ImpactScoringService and are read-only context:
 * no agent output can change them.
 */
public record DefectSnapshot(
        Long id,
        String title,
        String description,
        String environment,
        String service,
        String businessFunction,
        Integer affectedUsers,
        String businessCriticality,
        String customerImpact,
        String technicalImpact,
        String status,
        String project,
        String component,
        int deterministicImpactScore,
        String deterministicRiskLevel,
        String deterministicPriority
) {
}

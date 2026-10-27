package com.impactiq.dto;

import com.impactiq.entity.InvestigationStatus;

import java.time.Instant;
import java.util.List;

/** Dashboard investigation metrics, computed from persisted investigations, evidence and defects. */
public record InvestigationMetrics(
        long totalInvestigations,
        long activeInvestigations,
        long completedInvestigations,
        long partialInvestigations,
        long failedInvestigations,
        long retryEligibleInvestigations,
        long pendingReviewCount,
        long defectsWithEvidence,
        long investigatedDefects,
        int evidenceCoveragePercent,
        int investigationCoveragePercent,
        long unresolvedHighRiskCount,
        List<InvestigationActivity> recentInvestigations,
        List<InvestigationActivity> pendingReviews,
        List<InvestigationActivity> failedRuns,
        List<DashboardDefectSummary> unresolvedHighRisk
) {

    public record InvestigationActivity(
            Long id,
            Long defectId,
            String defectTitle,
            InvestigationStatus status,
            int attempt,
            boolean retryEligible,
            int hypothesisCount,
            String topHypothesis,
            String errorCategory,
            Instant createdAt,
            Instant completedAt
    ) {
    }
}

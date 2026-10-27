package com.impactiq.dto;

import java.util.List;

public record DashboardMetricsResponse(
        long totalDefects,
        long criticalDefects,
        long highDefects,
        long mediumDefects,
        long lowDefects,
        int averageImpactScore,
        List<DashboardChartPoint> defectsByRiskLevel,
        List<DashboardChartPoint> impactScoreDistribution,
        List<DashboardDefectSummary> topImpactDefects,
        List<DashboardChartPoint> defectsByEnvironment,
        List<DashboardChartPoint> defectsByService,
        InvestigationMetrics investigation
) {
}

package com.impactiq.dto;

public record DashboardDefectSummary(
        Long id,
        String title,
        int impactScore,
        String riskLevel,
        String priority,
        Integer aiImpactScore,
        int affectedUsers,
        String service,
        String environment
) {
}

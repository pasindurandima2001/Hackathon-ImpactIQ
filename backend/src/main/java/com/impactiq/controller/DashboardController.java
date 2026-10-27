package com.impactiq.controller;

import com.impactiq.dto.DashboardMetricsResponse;
import com.impactiq.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/metrics")
    public DashboardMetricsResponse getMetrics() {
        return dashboardService.getMetrics();
    }
}

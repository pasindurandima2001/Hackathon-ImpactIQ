package com.impactiq.service;

import com.impactiq.dto.DashboardChartPoint;
import com.impactiq.dto.DashboardDefectSummary;
import com.impactiq.dto.DashboardMetricsResponse;
import com.impactiq.dto.ImpactScoreResult;
import com.impactiq.entity.Defect;
import com.impactiq.entity.ImpactAnalysis;
import com.impactiq.repository.ImpactAnalysisRepository;
import com.impactiq.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final List<String> RISK_LEVELS = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    private static final List<String> SCORE_BANDS = List.of("0-29", "30-59", "60-79", "80-100");

    private final DefectService defectService;
    private final CurrentUserService currentUserService;
    private final ImpactAnalysisRepository impactAnalysisRepository;
    private final ImpactScoringService impactScoringService;
    private final InvestigationMetricsService investigationMetricsService;

    public DashboardService(
            DefectService defectService,
            CurrentUserService currentUserService,
            ImpactAnalysisRepository impactAnalysisRepository,
            ImpactScoringService impactScoringService,
            InvestigationMetricsService investigationMetricsService
    ) {
        this.defectService = defectService;
        this.currentUserService = currentUserService;
        this.impactAnalysisRepository = impactAnalysisRepository;
        this.impactScoringService = impactScoringService;
        this.investigationMetricsService = investigationMetricsService;
    }

    /** Metrics over the defects visible to the signed-in user (legacy defects plus their projects' defects). */
    public DashboardMetricsResponse getMetrics() {
        return getMetrics(defectService.visibleDefects(currentUserService.require()));
    }

    public DashboardMetricsResponse getMetrics(List<Defect> defects) {
        Map<Long, ImpactAnalysis> latestAnalyses = latestAnalysesByDefect();
        List<DashboardDefectSummary> summaries = defects.stream()
                .map(defect -> summarize(defect, latestAnalyses.get(defect.getId())))
                .toList();

        Map<String, Long> riskCounts = countsBy(summaries, DashboardDefectSummary::riskLevel);
        Map<String, Long> environmentCounts = countsBy(summaries, summary -> blankAs(summary.environment(), "UNSPECIFIED"));
        Map<String, Long> serviceCounts = countsBy(summaries, summary -> blankAs(summary.service(), "UNSPECIFIED"));
        Map<String, Long> scoreCounts = countsBy(summaries, summary -> scoreBand(summary.impactScore()));
        double average = summaries.stream().mapToInt(DashboardDefectSummary::impactScore).average().orElse(0);

        return new DashboardMetricsResponse(
                summaries.size(),
                riskCounts.getOrDefault("CRITICAL", 0L),
                riskCounts.getOrDefault("HIGH", 0L),
                riskCounts.getOrDefault("MEDIUM", 0L),
                riskCounts.getOrDefault("LOW", 0L),
                (int) Math.round(average),
                RISK_LEVELS.stream().map(level -> new DashboardChartPoint(level, riskCounts.getOrDefault(level, 0L))).toList(),
                SCORE_BANDS.stream().map(band -> new DashboardChartPoint(band, scoreCounts.getOrDefault(band, 0L))).toList(),
                summaries.stream()
                        .sorted(Comparator.comparingInt(DashboardDefectSummary::impactScore).reversed()
                                .thenComparing(DashboardDefectSummary::id, Comparator.reverseOrder()))
                        .limit(5)
                        .toList(),
                sortedPoints(environmentCounts),
                sortedPoints(serviceCounts),
                investigationMetricsService.compute(defects, summaries)
        );
    }

    private Map<Long, ImpactAnalysis> latestAnalysesByDefect() {
        return impactAnalysisRepository.findAll().stream()
                .filter(analysis -> analysis.getDefectId() != null)
                .collect(Collectors.toMap(
                        ImpactAnalysis::getDefectId,
                        Function.identity(),
                        this::newerAnalysis
                ));
    }

    private ImpactAnalysis newerAnalysis(ImpactAnalysis left, ImpactAnalysis right) {
        if (left.getCreatedAt() == null) return right;
        if (right.getCreatedAt() == null) return left;
        if (right.getCreatedAt().isAfter(left.getCreatedAt())) return right;
        if (right.getCreatedAt().equals(left.getCreatedAt())
                && right.getId() != null
                && (left.getId() == null || right.getId() > left.getId())) {
            return right;
        }
        return left;
    }

    /**
     * Risk counts, averages and ranking always use the deterministic score. The latest
     * AI-suggested score is reported separately and never drives dashboard metrics.
     */
    private DashboardDefectSummary summarize(Defect defect, ImpactAnalysis analysis) {
        ImpactScoreResult baseline = impactScoringService.calculate(defect);
        Integer aiImpactScore = analysis == null ? null : analysis.getImpactScore();
        return new DashboardDefectSummary(
                defect.getId(), defect.getTitle(), baseline.totalScore(), baseline.riskLevel(), baseline.priority(),
                aiImpactScore, safeUsers(defect), safe(defect.getService()),
                defect.getEnvironment() == null ? "" : defect.getEnvironment().name()
        );
    }

    private Map<String, Long> countsBy(List<DashboardDefectSummary> summaries, Function<DashboardDefectSummary, String> key) {
        return summaries.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.counting()));
    }

    private List<DashboardChartPoint> sortedPoints(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> new DashboardChartPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    private String scoreBand(int score) {
        if (score < 30) return "0-29";
        if (score < 60) return "30-59";
        if (score < 80) return "60-79";
        return "80-100";
    }

    private int safeUsers(Defect defect) {
        return defect.getAffectedUsers() == null ? 0 : defect.getAffectedUsers();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "UNSPECIFIED" : value;
    }

    private String blankAs(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

package com.impactiq.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.impactiq.config.InvestigationProperties;
import com.impactiq.dto.DashboardDefectSummary;
import com.impactiq.dto.InvestigationMetrics;
import com.impactiq.dto.InvestigationMetrics.InvestigationActivity;
import com.impactiq.entity.Defect;
import com.impactiq.entity.DefectStatus;
import com.impactiq.entity.Investigation;
import com.impactiq.entity.InvestigationStatus;
import com.impactiq.repository.DefectEvidenceRepository;
import com.impactiq.repository.InvestigationFeedbackRepository;
import com.impactiq.repository.InvestigationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Investigation-related dashboard metrics. All numbers are derived from persisted rows for the visible defects. */
@Service
@Transactional(readOnly = true)
public class InvestigationMetricsService {

    private static final int LIST_LIMIT = 6;
    private static final Set<String> HIGH_RISK = Set.of("HIGH", "CRITICAL");
    private static final Set<DefectStatus> UNRESOLVED = Set.of(DefectStatus.OPEN, DefectStatus.IN_PROGRESS);

    private final InvestigationRepository investigationRepository;
    private final DefectEvidenceRepository evidenceRepository;
    private final InvestigationFeedbackRepository feedbackRepository;
    private final InvestigationProperties properties;
    private final ObjectMapper objectMapper;

    public InvestigationMetricsService(
            InvestigationRepository investigationRepository,
            DefectEvidenceRepository evidenceRepository,
            InvestigationFeedbackRepository feedbackRepository,
            InvestigationProperties properties,
            ObjectMapper objectMapper
    ) {
        this.investigationRepository = investigationRepository;
        this.evidenceRepository = evidenceRepository;
        this.feedbackRepository = feedbackRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public InvestigationMetrics compute(List<Defect> defects, List<DashboardDefectSummary> summaries) {
        Map<Long, Defect> byId = defects.stream().collect(Collectors.toMap(Defect::getId, Function.identity()));
        List<Investigation> runs = investigationRepository.findAll().stream()
                .filter(run -> byId.containsKey(run.getDefectId()))
                .sorted(Comparator.comparing(Investigation::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Investigation::getId, Comparator.reverseOrder()))
                .toList();
        Set<Long> reviewed = feedbackRepository.findReviewedInvestigationIds();
        Set<Long> withEvidence = evidenceRepository.findDefectIdsWithEvidence().stream()
                .filter(byId::containsKey).collect(Collectors.toSet());
        Set<Long> investigated = runs.stream().map(Investigation::getDefectId).collect(Collectors.toSet());

        // Latest run per defect drives "pending review" and "failed" so superseded attempts do not double count.
        Map<Long, Investigation> latestPerDefect = runs.stream()
                .collect(Collectors.toMap(Investigation::getDefectId, Function.identity(), (newer, older) -> newer));
        List<Investigation> latest = latestPerDefect.values().stream()
                .sorted(Comparator.comparing(Investigation::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<Investigation> pending = latest.stream()
                .filter(run -> (run.getStatus() == InvestigationStatus.COMPLETED || run.getStatus() == InvestigationStatus.PARTIAL)
                        && !reviewed.contains(run.getId()))
                .toList();
        List<Investigation> failed = latest.stream().filter(run -> run.getStatus() == InvestigationStatus.FAILED).toList();

        List<DashboardDefectSummary> unresolvedHighRisk = summaries.stream()
                .filter(summary -> HIGH_RISK.contains(summary.riskLevel()))
                .filter(summary -> byId.get(summary.id()) != null && UNRESOLVED.contains(byId.get(summary.id()).getStatus()))
                .sorted(Comparator.comparingInt(DashboardDefectSummary::impactScore).reversed())
                .toList();

        return new InvestigationMetrics(
                runs.size(),
                count(runs, InvestigationStatus.QUEUED) + count(runs, InvestigationStatus.RUNNING),
                count(runs, InvestigationStatus.COMPLETED),
                count(runs, InvestigationStatus.PARTIAL),
                count(runs, InvestigationStatus.FAILED),
                latest.stream().filter(this::retryEligible).count(),
                pending.size(),
                withEvidence.size(),
                investigated.size(),
                percent(withEvidence.size(), defects.size()),
                percent(investigated.size(), defects.size()),
                unresolvedHighRisk.size(),
                runs.stream().limit(LIST_LIMIT).map(run -> activity(run, byId)).toList(),
                pending.stream().limit(LIST_LIMIT).map(run -> activity(run, byId)).toList(),
                failed.stream().limit(LIST_LIMIT).map(run -> activity(run, byId)).toList(),
                unresolvedHighRisk.stream().limit(LIST_LIMIT).toList()
        );
    }

    private InvestigationActivity activity(Investigation run, Map<Long, Defect> defects) {
        JsonNode hypotheses = hypotheses(run);
        int count = hypotheses == null ? 0 : hypotheses.size();
        return new InvestigationActivity(run.getId(), run.getDefectId(),
                defects.containsKey(run.getDefectId()) ? defects.get(run.getDefectId()).getTitle() : null,
                run.getStatus(), run.getAttempt(), retryEligible(run), count,
                count == 0 ? null : hypotheses.get(0).path("title").asText(null),
                run.getErrorCategory(), run.getCreatedAt(), run.getCompletedAt());
    }

    private boolean retryEligible(Investigation run) {
        return InvestigationStatus.RETRYABLE.contains(run.getStatus()) && run.getAttempt() < properties.getMaxAttempts();
    }

    private JsonNode hypotheses(Investigation run) {
        if (run.getReportJson() == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(run.getReportJson()).path("hypotheses");
            return node.isArray() ? node : null;
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private static long count(List<Investigation> runs, InvestigationStatus status) {
        return runs.stream().filter(run -> run.getStatus() == status).count();
    }

    private static int percent(long part, long total) {
        return total == 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }
}

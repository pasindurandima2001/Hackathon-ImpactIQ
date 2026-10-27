package com.impactiq.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.impactiq.entity.EvidenceType;
import com.impactiq.entity.FeedbackVerdict;
import com.impactiq.entity.InvestigationStatus;
import com.impactiq.entity.StepStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class InvestigationDtos {

    /** Pasted evidence limit (characters) — large enough for long stack traces and log excerpts. */
    public static final int EVIDENCE_MAX_CHARS = 200_000;

    private InvestigationDtos() {
    }

    public record EvidenceRequest(
            @NotNull(message = "Evidence type is required") EvidenceType type,
            @NotBlank(message = "Label is required") @Size(max = 255, message = "Label must be at most 255 characters")
            String label,
            @NotBlank(message = "Content is required")
            @Size(max = EVIDENCE_MAX_CHARS, message = "Content must be at most 200000 characters")
            String content
    ) {
    }

    public record EvidenceResponse(
            Long id,
            Long defectId,
            EvidenceType type,
            String label,
            String content,
            String sourceFilename,
            String contentSha256,
            int redactionCount,
            boolean suspicious,
            int lineCount,
            Instant createdAt
    ) {
    }

    public record StepResponse(
            Long id,
            String agentName,
            String displayName,
            int sequenceNumber,
            StepStatus status,
            int attempts,
            Long durationMs,
            String errorCategory,
            String errorMessage,
            String summary,
            Instant startedAt,
            Instant completedAt
    ) {
    }

    public record FeedbackRequest(
            @Min(1) @Max(5) Integer hypothesisRank,
            @NotNull(message = "Verdict is required") FeedbackVerdict verdict,
            @Size(max = 2000, message = "Comment must be at most 2000 characters") String comment
    ) {
    }

    public record FeedbackResponse(
            Long id,
            Long investigationId,
            Integer hypothesisRank,
            FeedbackVerdict verdict,
            String comment,
            String username,
            Instant createdAt
    ) {
    }

    public record InvestigationSummary(
            Long id,
            Long defectId,
            InvestigationStatus status,
            int attempt,
            Long parentInvestigationId,
            String engine,
            String model,
            int baselineScore,
            String baselineRiskLevel,
            String baselinePriority,
            String errorCategory,
            String errorMessage,
            boolean retryEligible,
            int hypothesisCount,
            String topHypothesis,
            int feedbackCount,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt
    ) {
    }

    public record InvestigationResponse(
            InvestigationSummary summary,
            List<StepResponse> steps,
            JsonNode report,
            List<FeedbackResponse> feedback
    ) {
    }
}

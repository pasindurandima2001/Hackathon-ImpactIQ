package com.impactiq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ChatDtos {

    /** Hard transport cap; the configurable limit (impactiq.chat.max-message-chars) is enforced by ChatService. */
    public static final int MESSAGE_TRANSPORT_MAX_CHARS = 20_000;
    public static final String ACTION_START_INVESTIGATION = "START_INVESTIGATION";

    private ChatDtos() {
    }

    public record CreateConversationRequest(
            @NotNull(message = "Project is required") Long projectId,
            Long componentId,
            Long defectId,
            @Size(max = 120, message = "Title must be at most 120 characters") String title
    ) {
    }

    /** When updateContext is true, componentId and defectId replace the current context (null clears them). */
    public record UpdateConversationRequest(
            @Size(max = 120, message = "Title must be at most 120 characters") String title,
            Boolean updateContext,
            Long componentId,
            Long defectId
    ) {
    }

    public record SendMessageRequest(
            @NotBlank(message = "Message is required")
            @Size(max = MESSAGE_TRANSPORT_MAX_CHARS, message = "Message is too long")
            String content
    ) {
    }

    public record ConversationSummary(
            Long id,
            String title,
            Long projectId,
            String projectName,
            Long componentId,
            String componentName,
            Long defectId,
            String defectTitle,
            int messageCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    /** Server-computed context. The client never supplies roles or permissions. */
    public record ContextInfo(
            String projectRole,
            boolean defectAvailable,
            String defectStatus,
            boolean investigationActive,
            Long latestInvestigationId,
            String latestInvestigationStatus
    ) {
    }

    public record ActionResponse(
            String type,
            boolean enabled,
            String label,
            String reason,
            Long defectId
    ) {
    }

    public record CitationSource(
            String type,
            Long documentId,
            Integer versionNumber,
            String filename,
            Long chunkId,
            Integer pageNumber,
            Integer startLine,
            Integer endLine,
            Long defectId,
            Long investigationId
    ) {
    }

    /** available=false means the cited document or chunk was deleted; the excerpt is then withheld. */
    public record CitationResponse(
            String id,
            String kind,
            String label,
            String excerpt,
            boolean available,
            boolean suspicious,
            CitationSource source
    ) {
    }

    public record MessageResponse(
            Long id,
            String role,
            String status,
            String content,
            String answerStatus,
            List<CitationResponse> citations,
            String clarifyingQuestion,
            List<String> missingInformation,
            List<String> followUpQuestions,
            List<String> warnings,
            int redactionCount,
            boolean suspicious,
            String errorCategory,
            Long durationMs,
            Instant createdAt,
            Instant completedAt
    ) {
    }

    public record ChatLimits(int maxMessageChars, int rateLimitPerMinute, int historyTurns) {
    }

    public record ConversationDetail(
            ConversationSummary conversation,
            ContextInfo context,
            List<MessageResponse> messages,
            List<ActionResponse> actions,
            List<String> suggestedQuestions,
            ChatLimits limits
    ) {
    }

    public record SendMessageResponse(
            ConversationSummary conversation,
            MessageResponse userMessage,
            MessageResponse assistantMessage,
            List<ActionResponse> actions
    ) {
    }

    public record SuggestionsResponse(List<String> questions, ChatLimits limits) {
    }
}

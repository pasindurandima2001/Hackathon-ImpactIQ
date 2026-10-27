package com.impactiq.dto;

import com.impactiq.entity.DocumentStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class KnowledgeDtos {

    private KnowledgeDtos() {
    }

    public record VersionResponse(
            Long id,
            int versionNumber,
            String originalFilename,
            long sizeBytes,
            String sha256,
            DocumentStatus status,
            String errorMessage,
            Integer chunkCount,
            Integer pageCount,
            String embeddingStatus,
            int redactionCount,
            boolean fileDeleted,
            boolean active,
            String uploadedBy,
            Instant createdAt,
            Instant processedAt
    ) {
    }

    public record DocumentResponse(
            Long id,
            Long projectId,
            Long componentId,
            String componentName,
            String title,
            String sourceType,
            DocumentStatus status,
            Integer activeVersionNumber,
            int latestVersionNumber,
            String lastError,
            String uploadedBy,
            Instant createdAt,
            Instant updatedAt,
            VersionResponse latestVersion
    ) {
    }

    public record DocumentDetailResponse(DocumentResponse document, List<VersionResponse> versions) {
    }

    public record SearchRequest(
            @NotBlank(message = "Query is required") @Size(max = 2000, message = "Query must be at most 2000 characters")
            String query,
            Long componentId,
            @Min(1) @Max(20) Integer topK
    ) {
    }

    public record SearchResultResponse(
            Long chunkId,
            Long documentId,
            String documentTitle,
            String filename,
            int versionNumber,
            Integer pageNumber,
            Integer startLine,
            Integer endLine,
            double score,
            double lexicalScore,
            double semanticScore,
            String excerpt,
            boolean suspicious
    ) {
    }

    public record SearchResponse(String mode, List<SearchResultResponse> results) {
    }
}

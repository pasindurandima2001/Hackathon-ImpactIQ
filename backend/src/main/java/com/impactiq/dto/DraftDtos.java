package com.impactiq.dto;

import com.impactiq.entity.BusinessCriticality;
import com.impactiq.entity.CustomerImpact;
import com.impactiq.entity.Environment;
import com.impactiq.entity.EvidenceType;
import com.impactiq.entity.TechnicalImpact;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class DraftDtos {

    public static final int DRAFT_INPUT_MAX_CHARS = 50_000;

    private DraftDtos() {
    }

    public enum DraftMode {
        QUICK,
        PASTE_ERROR,
        UPLOAD,
        AZURE_DEVOPS
    }

    public record AzureDevOpsDraftRequest(
            @NotBlank(message = "Paste the link of an Azure DevOps work item")
            @Size(max = 2048, message = "The link is too long")
            String url,
            Long projectId,
            Long componentId
    ) {
    }

    /** The source work item when a draft was imported from an external tracker. */
    public record ExternalWorkItem(
            String system,
            long id,
            String workItemType,
            String title,
            String state,
            String severity,
            String priority,
            String areaPath,
            String url,
            List<String> attachments,
            int commentCount
    ) {
    }

    public record DraftRequest(
            @NotNull(message = "Mode is required") DraftMode mode,
            @NotBlank(message = "Describe the problem or paste the error")
            @Size(max = DRAFT_INPUT_MAX_CHARS, message = "Input must be at most 50000 characters")
            String text,
            Long projectId,
            Long componentId
    ) {
    }

    /** Suggested evidence to attach after the developer confirms and saves the defect. */
    public record EvidenceDraft(
            EvidenceType type,
            String label,
            String content,
            String sourceFilename,
            int redactionCount
    ) {
    }

    /**
     * Editable suggestions; nothing is persisted until the developer reviews and submits the normal create request.
     * The impact score is always recalculated deterministically from the confirmed values.
     */
    public record DraftResponse(
            String source,
            String engine,
            String title,
            String description,
            Environment environment,
            String service,
            String businessFunction,
            Integer affectedUsers,
            BusinessCriticality businessCriticality,
            CustomerImpact customerImpact,
            TechnicalImpact technicalImpact,
            String category,
            List<String> missingInformation,
            String rationale,
            EvidenceDraft evidence,
            List<String> warnings,
            ExternalWorkItem externalItem
    ) {

        public DraftResponse(String source, String engine, String title, String description, Environment environment,
                             String service, String businessFunction, Integer affectedUsers,
                             BusinessCriticality businessCriticality, CustomerImpact customerImpact,
                             TechnicalImpact technicalImpact, String category, List<String> missingInformation,
                             String rationale, EvidenceDraft evidence, List<String> warnings) {
            this(source, engine, title, description, environment, service, businessFunction, affectedUsers,
                    businessCriticality, customerImpact, technicalImpact, category, missingInformation, rationale,
                    evidence, warnings, null);
        }
    }
}

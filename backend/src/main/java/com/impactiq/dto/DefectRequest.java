package com.impactiq.dto;

import com.impactiq.entity.BusinessCriticality;
import com.impactiq.entity.CustomerImpact;
import com.impactiq.entity.DefectStatus;
import com.impactiq.entity.Environment;
import com.impactiq.entity.Frequency;
import com.impactiq.entity.TechnicalImpact;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record DefectRequest(
        @NotBlank(message = "Title is required")
        @Size(max = DefectRequest.TITLE_MAX_LENGTH, message = "Title must be at most 255 characters")
        String title,
        @NotBlank(message = "Description is required")
        @Size(max = DefectRequest.DESCRIPTION_MAX_LENGTH, message = "Description must be at most 20000 characters")
        String description,
        Environment environment,
        @Size(max = DefectRequest.SHORT_TEXT_MAX_LENGTH, message = "Service must be at most 255 characters")
        String service,
        @Size(max = DefectRequest.SHORT_TEXT_MAX_LENGTH, message = "Business function must be at most 255 characters")
        String businessFunction,
        @PositiveOrZero(message = "Affected users must be zero or greater")
        Integer affectedUsers,
        Frequency frequency,
        BusinessCriticality businessCriticality,
        CustomerImpact customerImpact,
        TechnicalImpact technicalImpact,
        DefectStatus status,
        Long projectId,
        Long componentId
) {

    /** Constructor without project scope, kept for existing callers that create legacy (unscoped) defects. */
    public DefectRequest(
            String title,
            String description,
            Environment environment,
            String service,
            String businessFunction,
            Integer affectedUsers,
            Frequency frequency,
            BusinessCriticality businessCriticality,
            CustomerImpact customerImpact,
            TechnicalImpact technicalImpact,
            DefectStatus status
    ) {
        this(title, description, environment, service, businessFunction, affectedUsers, frequency,
                businessCriticality, customerImpact, technicalImpact, status, null, null);
    }

    /** Matches the default VARCHAR(255) column length of {@code defects.title}. */
    public static final int TITLE_MAX_LENGTH = 255;
    /** Matches the default VARCHAR(255) column length of {@code defects.service} and {@code defects.business_function}. */
    public static final int SHORT_TEXT_MAX_LENGTH = 255;
    public static final int DESCRIPTION_MAX_LENGTH = 20_000;
}

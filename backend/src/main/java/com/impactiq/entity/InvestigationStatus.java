package com.impactiq.entity;

import java.util.Set;

public enum InvestigationStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    PARTIAL,
    FAILED;

    public static final Set<InvestigationStatus> ACTIVE = Set.of(QUEUED, RUNNING);
    public static final Set<InvestigationStatus> RETRYABLE = Set.of(PARTIAL, FAILED);
}

package com.impactiq.entity;

public enum StepStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    /** Output copied from the parent run's successful step during a retry. */
    REUSED,
    SKIPPED,
    FAILED
}

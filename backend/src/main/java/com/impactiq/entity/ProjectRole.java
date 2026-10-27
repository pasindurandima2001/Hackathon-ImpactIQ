package com.impactiq.entity;

/** Ordered from least to most privileged; {@link #atLeast(ProjectRole)} relies on this order. */
public enum ProjectRole {
    VIEWER,
    EDITOR,
    OWNER;

    public boolean atLeast(ProjectRole required) {
        return ordinal() >= required.ordinal();
    }
}

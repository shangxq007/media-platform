package com.example.platform.timeline.diff.merge.plan;

/**
 * Severity of a merge plan issue.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public enum TimelineMergePlanIssueSeverity {
    INFO,
    WARNING,
    ERROR,
    BLOCKING
}

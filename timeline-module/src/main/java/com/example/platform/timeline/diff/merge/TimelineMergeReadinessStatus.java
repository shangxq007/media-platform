package com.example.platform.timeline.diff.merge;

/**
 * Readiness status for merge. Internal domain model.
 */
public enum TimelineMergeReadinessStatus {
    MERGE_READY,
    MANUAL_REVIEW_REQUIRED,
    BLOCKED,
    UNSUPPORTED,
    INVALID_INPUT
}

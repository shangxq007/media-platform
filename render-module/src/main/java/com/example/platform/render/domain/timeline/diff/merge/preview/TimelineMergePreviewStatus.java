package com.example.platform.render.domain.timeline.diff.merge.preview;

/**
 * Status of a merge preview result.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public enum TimelineMergePreviewStatus {
    MERGE_READY,
    MANUAL_REVIEW_REQUIRED,
    BLOCKED,
    INVALID_INPUT,
    UNSUPPORTED,
    FAILED
}

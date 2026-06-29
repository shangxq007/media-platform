package com.example.platform.render.domain.timeline.diff.merge.preview;

/**
 * Severity of a merge preview issue.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public enum TimelineMergePreviewIssueSeverity {
    INFO,
    WARNING,
    ERROR,
    BLOCKING
}

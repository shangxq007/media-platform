package com.example.platform.render.domain.timeline.diff.merge.preview;

/**
 * Mode controlling what the preview includes.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public enum TimelineMergePreviewMode {
    CONFLICTS_ONLY,
    DIFF_AND_CONFLICTS,
    READINESS_ONLY
}

package com.example.platform.render.domain.timeline.diff.merge.preview;

/**
 * Code identifying the type of merge preview issue.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public enum TimelineMergePreviewIssueCode {
    MISSING_BASE,
    MISSING_OURS,
    MISSING_THEIRS,
    INVALID_REQUEST,
    CONFLICT_ANALYSIS_FAILED,
    MANUAL_REVIEW_REQUIRED,
    UNSUPPORTED_PREVIEW_MODE,
    PROVIDER_INTERNALS_NOT_ALLOWED,
    STORAGE_INTERNALS_NOT_ALLOWED,
    EXECUTION_NOT_ALLOWED
}

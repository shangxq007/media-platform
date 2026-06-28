package com.example.platform.render.domain.template.composite;

/**
 * Conflict policy for composite template child operations.
 * Internal domain model — vocabulary only, no conflict resolution.
 */
public enum CompositeTemplateConflictPolicy {
    FAIL_FAST,
    PARENT_OVERRIDES,
    CHILD_OVERRIDES,
    MERGE_IF_COMPATIBLE,
    WARN_AND_CONTINUE,
    MANUAL_REVIEW_REQUIRED
}

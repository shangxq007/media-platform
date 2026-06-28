package com.example.platform.render.domain.timeline.diff;

/**
 * Merge policy for resolving timeline conflicts.
 * Internal domain model — vocabulary only, no execution.
 */
public enum TimelineMergePolicy {
    FAIL_FAST,
    BASELINE_WINS,
    INCOMING_WINS,
    MERGE_IF_COMPATIBLE,
    MANUAL_REVIEW_REQUIRED
}

package com.example.platform.render.domain.timeline.diff;

/**
 * Render impact level for a timeline change.
 * Internal domain model — vocabulary only, no computation.
 */
public enum TimelineRenderImpactLevel {
    NONE,
    METADATA_ONLY,
    PREVIEW_ONLY,
    PARTIAL_RERENDER,
    FULL_RERENDER,
    UNKNOWN
}

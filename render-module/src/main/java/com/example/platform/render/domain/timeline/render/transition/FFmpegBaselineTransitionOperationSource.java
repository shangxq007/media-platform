package com.example.platform.render.domain.timeline.render.transition;

/**
 * Source of an FFmpeg baseline transition operation.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegBaselineTransitionOperationSource {
    BASIC_TIMELINE_TRANSITION_REF,
    VISUAL_CAPABILITY_RESOLVED,
    POLICY_DEFAULT,
    INTERNAL_ANNOTATION
}

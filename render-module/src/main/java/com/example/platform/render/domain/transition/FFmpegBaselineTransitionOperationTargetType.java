package com.example.platform.render.domain.transition;

/**
 * Target types for FFmpeg baseline transition operations.
 * Immutable enum. Internal domain model.
 */
public enum FFmpegBaselineTransitionOperationTargetType {
    TIMELINE,
    TRACK,
    CLIP_PAIR,
    TRANSITION
}

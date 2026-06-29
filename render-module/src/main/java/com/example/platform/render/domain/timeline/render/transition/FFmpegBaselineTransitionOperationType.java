package com.example.platform.render.domain.timeline.render.transition;

/**
 * FFmpeg baseline transition operation types.
 * Immutable enum. Internal domain model.
 *
 * <p>Baseline (PRODUCTION): CUT, FADE, CROSSFADE, DISSOLVE.
 * POC: SLIDE, WIPE, PUSH, ZOOM.</p>
 *
 * <p>Forbidden transitions are not represented here — they are blocked
 * at the capability validation layer.</p>
 */
public enum FFmpegBaselineTransitionOperationType {
    // Baseline / Production
    CUT,
    FADE,
    CROSSFADE,
    DISSOLVE,
    // POC / Future
    SLIDE,
    WIPE,
    PUSH,
    ZOOM
}

package com.example.platform.render.domain.timeline.editing;

/**
 * Semantic edit operation types for timeline editing.
 * Immutable enum. Internal domain model.
 *
 * <p>Operations are semantic, side-effect-free, and do not persist or render.</p>
 */
public enum TimelineEditOperationType {
    CREATE_TIMELINE,
    UPDATE_OUTPUT_PROFILE,
    ADD_TRACK,
    REMOVE_TRACK,
    REORDER_TRACK,
    ADD_CLIP,
    UPDATE_CLIP,
    REMOVE_CLIP,
    ADD_CAPTION,
    UPDATE_CAPTION,
    REMOVE_CAPTION,
    ADD_WATERMARK,
    UPDATE_WATERMARK,
    REMOVE_WATERMARK,
    ADD_EFFECT,
    UPDATE_EFFECT,
    REMOVE_EFFECT,
    ADD_TRANSITION,
    UPDATE_TRANSITION,
    REMOVE_TRANSITION,
    VALIDATE_TIMELINE
}

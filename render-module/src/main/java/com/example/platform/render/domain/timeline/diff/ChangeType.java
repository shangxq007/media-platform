package com.example.platform.render.domain.timeline.diff;

/**
 * Change type classification for semantic diff.
 */
public enum ChangeType {
    // Track changes
    TRACK_ADDED,
    TRACK_REMOVED,
    TRACK_PROPERTY_CHANGED,
    TRACK_REORDERED,

    // Clip changes
    CLIP_ADDED,
    CLIP_REMOVED,
    CLIP_PROPERTY_CHANGED,
    CLIP_MOVED,
    CLIP_REORDERED,

    // Generic
    ADDED,
    REMOVED,
    PROPERTY_CHANGED,
    REORDERED
}

package com.example.platform.render.domain.timeline.internal;

/**
 * Types of conflicts that can occur during timeline merge.
 */
public enum TimelineConflictType {
    SAME_ENTITY_MODIFIED,
    CLIP_RANGE_CONFLICT,
    CLIP_REMOVED_AND_MODIFIED,
    CLIP_MOVED_CONFLICT,
    EFFECT_CONFLICT,
    METADATA_CONFLICT,
    TRACK_STRUCTURE_CONFLICT,
    MARKER_CONFLICT,
    UNKNOWN
}

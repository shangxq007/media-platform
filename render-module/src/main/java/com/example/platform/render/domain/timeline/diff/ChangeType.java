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
    REORDERED,

    // AUDIO_V2 (A13): document-level canonical audio mix semantic change
    AUDIO_MIX_CHANGED,

    // SEMANTIC_RELATIONSHIP_SELECTION_POST_CLOSE: typed relationship semantic diff
    RELATIONSHIP_ADDED,
    RELATIONSHIP_REMOVED,
    SYNC_ANCHOR_CHANGED,
    GROUP_MEMBER_ADDED,
    GROUP_MEMBER_REMOVED
}

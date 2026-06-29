package com.example.platform.render.domain.timeline.diff.merge.preview;

/**
 * Policy controlling how the preview interprets conflicts.
 * Vocabulary only — does not implement automatic merge or conflict resolution.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public enum TimelineMergePreviewPolicy {
    CONSERVATIVE,
    ALLOW_IDENTICAL_SAME_PATH_CHANGES,
    BLOCK_ON_ANY_CONFLICT
}

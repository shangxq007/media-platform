package com.example.platform.render.domain.timeline.compile;

/**
 * Types of artifact nodes in the compile-time dependency graph.
 *
 * <p>These are compile-time planning node types, distinct from the
 * job-level {@link com.example.platform.render.domain.artifact.ArtifactNodeType}
 * which tracks file types of produced artifacts.</p>
 *
 * <p>v0 node types cover the minimal safe subset. Unsupported types
 * fail closed during compile.</p>
 */
public enum ArtifactNodeType {

    /**
     * Input media asset (source RAW_MEDIA Product).
     */
    INPUT_MEDIA,

    /**
     * Trimmed/segmented media from a clip.
     */
    TRIMMED_MEDIA,

    /**
     * Subtitle/caption overlay layer.
     */
    SUBTITLE_OVERLAY,

    /**
     * Audio mix from one or more audio tracks.
     */
    AUDIO_MIX,

    /**
     * Final encode (video + audio into container).
     */
    FINAL_ENCODE,

    /**
     * Final render output (the canonical output Product).
     */
    FINAL_RENDER
}

package com.example.platform.execution.domain;

/**
 * Role classification for an execution input.
 *
 * <p>Closed, version-governed enum — serialized by name for canonical representation.
 * The input role describes the semantic purpose of the input in the execution plan,
 * not the storage location or file format.
 */
public enum ExecutionInputRole {
    /**
     * Primary media input — the main artifact being processed.
     */
    PRIMARY_MEDIA,
    /**
     * Secondary media input — overlay, background, or supplementary content.
     */
    SECONDARY_MEDIA,
    /**
     * Audio input — soundtrack, narration, or audio track.
     */
    AUDIO,
    /**
     * Video input — primary or secondary video content.
     */
    VIDEO,
    /**
     * Image input — still image, thumbnail source, or graphic asset.
     */
    IMAGE,
    /**
     * Subtitle input — caption, subtitle, or text overlay source.
     */
    SUBTITLE,
    /**
     * Font input — typography resource for text rendering.
     */
    FONT,
    /**
     * Overlay input — graphic layer composited over primary media.
     */
    OVERLAY,
    /**
     * LUT input — color lookup table for color grading.
     */
    LUT,
    /**
     * Model input — machine learning model for inference operations.
     */
    MODEL_INPUT,
    /**
     * Manifest input — DASH/HLS manifest or playlist.
     */
    MANIFEST
}

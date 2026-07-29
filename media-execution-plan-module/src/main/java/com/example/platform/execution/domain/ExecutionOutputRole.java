package com.example.platform.execution.domain;

/**
 * Role classification for an execution output.
 *
 * <p>Closed, version-governed enum — serialized by name for canonical representation.
 * The output role describes the semantic purpose of the output artifact,
 * not its storage location or file format.
 */
public enum ExecutionOutputRole {
    /**
     * Primary output — the main deliverable of the execution plan.
     */
    PRIMARY_OUTPUT,
    /**
     * Intermediate output — temporary artifact consumed by later steps.
     */
    INTERMEDIATE,
    /**
     * Proxy output — low-resolution preview or editing proxy.
     */
    PROXY,
    /**
     * Thumbnail output — static image preview.
     */
    THUMBNAIL,
    /**
     * Waveform output — audio waveform visualization data.
     */
    WAVEFORM,
    /**
     * Subtitle output — rendered or extracted subtitle.
     */
    SUBTITLE,
    /**
     * Analysis output — metadata, reports, or analysis results.
     */
    ANALYSIS,
    /**
     * Render master output — full-quality master for downstream delivery.
     */
    RENDER_MASTER,
    /**
     * Delivery rendition output — specific format/bitrate for delivery.
     */
    DELIVERY_RENDITION,
    /**
     * Manifest output — DASH/HLS manifest or playlist.
     */
    MANIFEST
}

package com.example.platform.artifact.domain;

/**
 * Types of artifacts that can be registered in the artifact catalog.
 *
 * <p>Artifacts are the outputs of render, packaging, and processing jobs.
 * Each artifact has a type that describes its content and purpose.</p>
 */
public enum ArtifactType {

    // Legacy / generic
    GENERIC,
    VIDEO,
    AUDIO,
    IMAGE,
    DOCUMENT,

    // Timeline formats
    TIMELINE_JSON,
    TIMELINE_OTIO,
    MLT_PROJECT_XML,

    // Render artifacts
    FFMPEG_COMMAND_SPEC,
    RENDER_LOG,
    VIDEO_MEZZANINE,
    VIDEO_MP4,
    VIDEO_PROXY,
    THUMBNAIL,

    // Subtitle formats
    SUBTITLE_SRT,
    SUBTITLE_VTT,

    // Audio
    AUDIO_MIXDOWN,

    // HLS packaging
    HLS_MANIFEST,
    HLS_SEGMENT,

    // DASH packaging
    DASH_MANIFEST,
    DASH_SEGMENT,

    // CMAF
    CMAF_CHUNK,

    // Quality control
    QC_REPORT,

    // Media probe
    MEDIA_PROBE_JSON
}

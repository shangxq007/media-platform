package com.example.platform.render.domain;

/**
 * Types of steps that can appear in a {@link RenderPlan}.
 *
 * <p>Each step type corresponds to a specific operation in the render pipeline,
 * from timeline construction through transcoding, packaging, and quality control.</p>
 */
public enum RenderStepType {

    /** Build the internal timeline model from user input. */
    BUILD_TIMELINE,

    /** Probe source media with FFprobe to extract metadata. */
    FFMPEG_PROBE,

    /** Transcode media using FFmpeg. */
    FFMPEG_TRANSCODE,

    /** Render a multi-track timeline using MLT/melt. */
    MLT_RENDER_TIMELINE,

    /** Package media into HLS format using GPAC/MP4Box. */
    GPAC_PACKAGE_HLS,

    /** Package media into DASH format using GPAC/MP4Box. */
    GPAC_PACKAGE_DASH,

    /** Register the output artifact in the artifact catalog. */
    REGISTER_ARTIFACT,

    /** Run quality control probe on the output. */
    QC_PROBE
}

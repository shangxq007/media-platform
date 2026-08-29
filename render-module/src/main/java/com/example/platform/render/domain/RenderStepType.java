package com.example.platform.render.domain;

/**
 * Types of steps that can appear in a {@link RenderJobPlan}.
 *
 * <p>Each step type corresponds to a specific operation in the render pipeline,
 * from timeline construction through transcoding, packaging, and quality control.</p>
 */
public enum RenderStepType {

    /** Build the internal timeline model from user input. */
    BUILD_TIMELINE,

    /** Probe source media with MediaProbe to extract metadata. */
    PROVIDER_PROBE,

    /** Transcode media using Provider. */
    PROVIDER_TRANSCODE,

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

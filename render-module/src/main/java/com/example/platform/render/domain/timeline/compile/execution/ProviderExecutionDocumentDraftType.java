package com.example.platform.render.domain.timeline.compile.execution;

/**
 * Types of provider execution document drafts.
 *
 * <p>Internal only — represents the planned document type
 * without generating actual content.</p>
 */
public enum ProviderExecutionDocumentDraftType {

    /** FFmpeg command plan with args and filter graph. */
    FFMPEG_COMMAND_PLAN,

    /** MLT project XML document. */
    MLT_PROJECT_DOCUMENT,

    /** Remotion input props document. */
    REMOTION_INPUT_PROPS_DOCUMENT,

    /** Blender scene specification. */
    BLENDER_SCENE_SPEC,

    /** Natron project specification. */
    NATRON_PROJECT_SPEC,

    /** GPAC/MP4Box packaging plan. */
    PACKAGING_PLAN,

    /** GStreamer pipeline specification. */
    GSTREAMER_PIPELINE_SPEC,

    /** OpenFX effect descriptor (no executable without host). */
    OPENFX_EFFECT_DESCRIPTOR,

    /** Generic/unknown document type. */
    UNKNOWN
}

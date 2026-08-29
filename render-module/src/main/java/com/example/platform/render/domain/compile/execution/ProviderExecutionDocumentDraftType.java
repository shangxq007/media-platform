package com.example.platform.render.domain.compile.execution;

/**
 * Types of provider execution document drafts.
 *
 * <p>Internal only — represents the planned document type
 * without generating actual content.</p>
 */
public enum ProviderExecutionDocumentDraftType {

    /** Opaque typed-provider request; carries no argv, filter graph, or command and is not generation-ready. */
    TYPED_PROVIDER_REQUEST,

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

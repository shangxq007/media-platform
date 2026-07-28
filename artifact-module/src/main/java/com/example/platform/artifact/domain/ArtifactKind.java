package com.example.platform.artifact.domain;

/**
 * Classifies the role an Artifact plays in the media platform.
 *
 * <p>Stable, closed enum — serialized by name for canonical representation.
 * Artifact Kind is NOT derived from a render backend and MUST NOT be added to any
 * render backend enum.
 */
public enum ArtifactKind {
    SOURCE_MEDIA,
    DERIVED_MEDIA,
    PROXY,
    THUMBNAIL,
    WAVEFORM,
    SUBTITLE,
    TRANSCRIPT,
    ANALYSIS_RESULT,
    GENERATED_MEDIA,
    RENDER_MASTER,
    DELIVERY_RENDITION,
    MANIFEST
}

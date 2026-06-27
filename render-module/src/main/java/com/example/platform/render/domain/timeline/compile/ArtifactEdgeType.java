package com.example.platform.render.domain.timeline.compile;

/**
 * Types of dependency edges in the artifact dependency graph.
 */
public enum ArtifactEdgeType {

    /**
     * Target is a source/ancestor of the source node.
     * Example: TRIMMED_MEDIA DERIVES_FROM INPUT_MEDIA
     */
    DERIVES_FROM,

    /**
     * Source requires target as input.
     * Example: AUDIO_MIX REQUIRES_INPUT INPUT_MEDIA
     */
    REQUIRES_INPUT,

    /**
     * Source encodes/transcodes to target.
     * Example: TRIMMED_MEDIA ENCODES_TO FINAL_ENCODE
     */
    ENCODES_TO,

    /**
     * Source produces target as output.
     * Example: FINAL_ENCODE PRODUCES FINAL_RENDER
     */
    PRODUCES
}

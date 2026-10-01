package com.example.platform.artifact.domain;

/**
 * Type of derivation relationship between two Artifacts in the provenance graph.
 *
 * <p>Stable, closed enum — serialized by name for canonical representation.
 * v1 only validates DAG for derivation provenance relationships.
 */
public enum ProvenanceRelationType {
    GENERATED_FROM,
    TRANSCODED_FROM,
    EXTRACTED_FROM,
    COMPOSED_FROM,
    ANALYZED_FROM,
    UPGRADED_FROM,
    DENOISED_FROM,
    SUBTITLED_FROM,
    RENDERED_FROM,
    /** Cover image derived from its subject Artifact (relation-based cover representation). */
    COVER_OF,
    /**
     * Thumbnail image derived from its subject Artifact (relation-based thumbnail representation).
     *
     * <p>COVER-THUMBNAIL-REBUILD-001 (action 5): a thumbnail is an image Artifact that plays the
     * thumbnail role through this provenance relation — the same relation-based model the cover
     * capability uses ({@link #COVER_OF}) — instead of a dedicated {@code ArtifactKind}.
     */
    THUMBNAIL_OF
}

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
    RENDERED_FROM
}

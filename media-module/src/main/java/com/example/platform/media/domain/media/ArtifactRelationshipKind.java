package com.example.platform.media.domain.media;

/**
 * Relationship kind between a MediaAsset and an Artifact
 * (MEDIA_ASSET_ARTIFACT_RELATIONSHIP_V1).
 */
public enum ArtifactRelationshipKind {
    /** The artifact is the physical content backing the source media. */
    SOURCE,
    /** The artifact is a derived output (proxy, thumbnail, analysis, rendition). */
    DERIVED
}

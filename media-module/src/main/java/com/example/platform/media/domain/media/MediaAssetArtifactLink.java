package com.example.platform.media.domain.media;

import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.media.domain.identity.MediaAssetId;
import java.io.Serializable;

/**
 * MEDIA_ASSET_ARTIFACT_RELATIONSHIP_V1 — typed relationship between a source
 * MediaAsset and a physical/generated Artifact.
 *
 * <p>One MediaAsset may link to multiple ArtifactIds (source content version,
 * proxy, thumbnail, analysis output, rendition). A new physical content
 * version is a NEW ArtifactId linked to the SAME MediaAssetId. The artifact
 * lifecycle (commit/provenance/GC) remains owned by artifact authority.
 */
public record MediaAssetArtifactLink(
        MediaAssetId mediaAssetId,
        ArtifactId artifactId,
        ArtifactRelationshipKind relationship) implements Serializable {

    public MediaAssetArtifactLink {
        if (mediaAssetId == null) {
            throw new IllegalArgumentException("mediaAssetId must not be null");
        }
        if (artifactId == null) {
            throw new IllegalArgumentException("artifactId must not be null");
        }
        if (relationship == null) {
            throw new IllegalArgumentException("relationship must not be null");
        }
    }
}

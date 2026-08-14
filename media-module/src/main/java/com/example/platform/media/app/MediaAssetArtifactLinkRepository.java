package com.example.platform.media.app;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.media.ArtifactRelationshipKind;
import com.example.platform.media.domain.media.MediaAssetArtifactLink;
import com.example.platform.shared.identity.ArtifactId;
import java.util.List;

/**
 * MEDIA_ASSET_ARTIFACT_RELATIONSHIP_V1 persistence port.
 */
public interface MediaAssetArtifactLinkRepository {

    void save(MediaAssetArtifactLink link);

    List<ArtifactId> findArtifactIds(MediaAssetId mediaAssetId, ArtifactRelationshipKind kind);

    List<MediaAssetId> findMediaAssetIds(ArtifactId artifactId);
}

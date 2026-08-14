package com.example.platform.media.infrastructure.persistence;

import static com.example.platform.typedschema.jooq.generated.tables.MediaAssetArtifact.MEDIA_ASSET_ARTIFACT;

import com.example.platform.media.app.MediaAssetArtifactLinkRepository;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.media.ArtifactRelationshipKind;
import com.example.platform.media.domain.media.MediaAssetArtifactLink;
import com.example.platform.shared.identity.ArtifactId;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * jOOQ implementation of the typed MediaAsset<->Artifact linkage
 * (MEDIA_ASSET_ARTIFACT_RELATIONSHIP_V1).
 */
@Repository
public class JooqMediaAssetArtifactLinkRepository implements MediaAssetArtifactLinkRepository {

    private final DSLContext dsl;

    public JooqMediaAssetArtifactLinkRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void save(MediaAssetArtifactLink link) {
        dsl.insertInto(MEDIA_ASSET_ARTIFACT)
                .columns(MEDIA_ASSET_ARTIFACT.MEDIA_ASSET_ID, MEDIA_ASSET_ARTIFACT.ARTIFACT_ID,
                        MEDIA_ASSET_ARTIFACT.RELATIONSHIP, MEDIA_ASSET_ARTIFACT.CREATED_AT)
                .values(link.mediaAssetId().value(), link.artifactId().value(),
                        link.relationship().name(), LocalDateTime.now(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute();
    }

    @Override
    public List<ArtifactId> findArtifactIds(MediaAssetId mediaAssetId, ArtifactRelationshipKind kind) {
        return dsl.select(MEDIA_ASSET_ARTIFACT.ARTIFACT_ID)
                .from(MEDIA_ASSET_ARTIFACT)
                .where(MEDIA_ASSET_ARTIFACT.MEDIA_ASSET_ID.eq(mediaAssetId.value()))
                .and(MEDIA_ASSET_ARTIFACT.RELATIONSHIP.eq(kind.name()))
                .fetch(MEDIA_ASSET_ARTIFACT.ARTIFACT_ID)
                .stream().map(ArtifactId::new).toList();
    }

    @Override
    public List<MediaAssetId> findMediaAssetIds(ArtifactId artifactId) {
        return dsl.select(MEDIA_ASSET_ARTIFACT.MEDIA_ASSET_ID)
                .from(MEDIA_ASSET_ARTIFACT)
                .where(MEDIA_ASSET_ARTIFACT.ARTIFACT_ID.eq(artifactId.value()))
                .fetch(MEDIA_ASSET_ARTIFACT.MEDIA_ASSET_ID)
                .stream().map(MediaAssetId::new).toList();
    }
}

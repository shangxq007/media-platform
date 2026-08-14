package com.example.platform.media.infrastructure.persistence;

import static com.example.platform.typedschema.jooq.generated.tables.MediaAsset.MEDIA_ASSET;

import com.example.platform.media.app.MediaAssetRepository;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.locator.ExternalLocator;
import com.example.platform.media.domain.media.MediaAsset;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/**
 * jOOQ implementation of {@link MediaAssetRepository} over the canonical
 * media_asset table (MCMV2-C).
 */
@Repository
public class JooqMediaAssetRepository implements MediaAssetRepository {

    private final DSLContext dsl;

    public JooqMediaAssetRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public MediaAsset save(MediaAsset asset) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        boolean exists = exists(asset.id());
        if (exists) {
            dsl.update(MEDIA_ASSET)
                    .set(MEDIA_ASSET.PROJECT_ID, asset.projectId())
                    .set(MEDIA_ASSET.MEDIA_VERSION, asset.mediaVersion())
                    .set(MEDIA_ASSET.ENTITY_REF, asset.externalLocator() != null ? asset.externalLocator().value() : null)
                    .set(MEDIA_ASSET.CLASSIFICATION, asset.classification())
                    .set(MEDIA_ASSET.LICENSE, asset.license())
                    .set(MEDIA_ASSET.RETENTION_POLICY, asset.retentionPolicy())
                    .set(MEDIA_ASSET.SECURITY_LEVEL, asset.securityLevel())
                    .set(MEDIA_ASSET.CONTAINS_PII, asset.containsPii())
                    .set(MEDIA_ASSET.AI_GENERATED, asset.aiGenerated())
                    .set(MEDIA_ASSET.PUBLISH_STATUS, asset.publishStatus())
                    .set(MEDIA_ASSET.UPDATED_AT, now)
                    .where(MEDIA_ASSET.ID.eq(asset.id().value()))
                    .execute();
        } else {
            dsl.insertInto(MEDIA_ASSET)
                    .columns(MEDIA_ASSET.ID, MEDIA_ASSET.TENANT_ID, MEDIA_ASSET.PROJECT_ID,
                            MEDIA_ASSET.STORAGE_KEY, MEDIA_ASSET.MEDIA_TYPE, MEDIA_ASSET.FILENAME,
                            MEDIA_ASSET.SIZE_BYTES, MEDIA_ASSET.CHECKSUM, MEDIA_ASSET.MEDIA_VERSION,
                            MEDIA_ASSET.OWNER_ID, MEDIA_ASSET.ENTITY_REF, MEDIA_ASSET.CLASSIFICATION,
                            MEDIA_ASSET.LICENSE, MEDIA_ASSET.RETENTION_POLICY, MEDIA_ASSET.SECURITY_LEVEL,
                            MEDIA_ASSET.CONTAINS_PII, MEDIA_ASSET.AI_GENERATED, MEDIA_ASSET.CREATED_AT,
                            MEDIA_ASSET.UPDATED_AT, MEDIA_ASSET.PUBLISH_STATUS)
                    .values(asset.id().value(), asset.tenantId(), asset.projectId(),
                            "", "UNKNOWN", null, null, null, asset.mediaVersion(),
                            null, asset.externalLocator() != null ? asset.externalLocator().value() : null,
                            asset.classification(), asset.license(), asset.retentionPolicy(),
                            asset.securityLevel(), asset.containsPii(), asset.aiGenerated(),
                            now, now, asset.publishStatus())
                    .execute();
        }
        return asset;
    }

    @Override
    public Optional<MediaAsset> findById(MediaAssetId id) {
        var row = dsl.selectFrom(MEDIA_ASSET)
                .where(MEDIA_ASSET.ID.eq(id.value()))
                .fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new MediaAsset(
                MediaAssetId.of(row.get(MEDIA_ASSET.ID)),
                row.get(MEDIA_ASSET.TENANT_ID),
                row.get(MEDIA_ASSET.PROJECT_ID),
                row.get(MEDIA_ASSET.MEDIA_VERSION),
                row.get(MEDIA_ASSET.ENTITY_REF) != null
                        ? new ExternalLocator("entityRef", row.get(MEDIA_ASSET.ENTITY_REF)) : null,
                row.get(MEDIA_ASSET.CLASSIFICATION),
                row.get(MEDIA_ASSET.LICENSE),
                row.get(MEDIA_ASSET.RETENTION_POLICY),
                row.get(MEDIA_ASSET.SECURITY_LEVEL),
                Boolean.TRUE.equals(row.get(MEDIA_ASSET.CONTAINS_PII)),
                Boolean.TRUE.equals(row.get(MEDIA_ASSET.AI_GENERATED)),
                row.get(MEDIA_ASSET.PUBLISH_STATUS),
                row.get(MEDIA_ASSET.CREATED_AT) != null ? row.get(MEDIA_ASSET.CREATED_AT).toInstant(ZoneOffset.UTC) : null,
                row.get(MEDIA_ASSET.UPDATED_AT) != null ? row.get(MEDIA_ASSET.UPDATED_AT).toInstant(ZoneOffset.UTC) : null));
    }

    @Override
    public boolean exists(MediaAssetId id) {
        return dsl.fetchExists(dsl.selectOne().from(MEDIA_ASSET).where(MEDIA_ASSET.ID.eq(id.value())));
    }
}

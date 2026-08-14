package com.example.platform.render.infrastructure.asset;

import com.example.platform.render.domain.asset.Asset;
import com.example.platform.shared.Ids;
import com.example.platform.storage.contract.StorageKeyPolicy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.MediaAsset.MEDIA_ASSET;


/**
 * Repository for project assets (MCMV2-C: canonical media_asset table).
 *
 * <p>All storage keys are validated via {@link StorageKeyPolicy} before
 * persistence. Structural media truth (duration/streams/codecs) does NOT live
 * on this row — it lives in the canonical media structural model
 * (media-module MediaStream / NormalizedMediaProbe). The storage key column is
 * a STORAGE_REF projection, never media identity.
 */
@Repository
public class AssetRepository {

    private final DSLContext dsl;

    public AssetRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Register a new asset.
     */
    public Asset register(String tenantId, String projectId, String storageKey,
                          String mediaType, String filename, Long sizeBytes,
                          String checksum) {
        // Validate storage key via policy
        StorageKeyPolicy.assertValidPath(storageKey);

        String id = Ids.newId("asset");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        dsl.insertInto(MEDIA_ASSET)
                .set(MEDIA_ASSET.ID, id)
                .set(MEDIA_ASSET.TENANT_ID, tenantId)
                .set(MEDIA_ASSET.PROJECT_ID, projectId)
                .set(MEDIA_ASSET.STORAGE_KEY, storageKey)
                .set(MEDIA_ASSET.MEDIA_TYPE, mediaType)
                .set(MEDIA_ASSET.FILENAME, filename)
                .set(MEDIA_ASSET.SIZE_BYTES, sizeBytes)
                .set(MEDIA_ASSET.CHECKSUM, checksum)
                .set(MEDIA_ASSET.MEDIA_VERSION, "v1")
                .set(MEDIA_ASSET.CONTAINS_PII, false)
                .set(MEDIA_ASSET.AI_GENERATED, false)
                .set(MEDIA_ASSET.CREATED_AT, now)
                .set(MEDIA_ASSET.UPDATED_AT, now)
                .set(MEDIA_ASSET.PUBLISH_STATUS, "DRAFT")
                .execute();

        return new Asset(id, tenantId, projectId, storageKey, mediaType, filename,
                sizeBytes, checksum,
                "v1", null, null, null, null, null, null, false, false, "DRAFT",
                now.toInstant(ZoneOffset.UTC), now.toInstant(ZoneOffset.UTC));
    }

    /**
     * Find an asset by ID, scoped to tenant.
     */
    public Optional<Asset> findById(String tenantId, String assetId) {
        Record r = dsl.selectFrom(MEDIA_ASSET)
                .where(MEDIA_ASSET.ID.eq(assetId))
                .and(MEDIA_ASSET.TENANT_ID.eq(tenantId))
                .fetchOne();
        return Optional.ofNullable(r).map(this::mapAsset);
    }

    /**
     * List all assets for a project, scoped to tenant.
     */
    public List<Asset> listByProject(String tenantId, String projectId) {
        return dsl.selectFrom(MEDIA_ASSET)
                .where(MEDIA_ASSET.TENANT_ID.eq(tenantId))
                .and(MEDIA_ASSET.PROJECT_ID.eq(projectId))
                .orderBy(MEDIA_ASSET.CREATED_AT.desc())
                .fetch(this::mapAsset);
    }

    /**
     * Delete an asset by ID, scoped to tenant.
     */
    public boolean delete(String tenantId, String assetId) {
        return dsl.deleteFrom(MEDIA_ASSET)
                .where(MEDIA_ASSET.ID.eq(assetId).and(MEDIA_ASSET.TENANT_ID.eq(tenantId)))
                .execute() > 0;
    }

    public void updatePublishStatus(String tenantId, String assetId, String publishStatus) {
        dsl.update(MEDIA_ASSET)
                .set(MEDIA_ASSET.PUBLISH_STATUS, publishStatus)
                .where(MEDIA_ASSET.ID.eq(assetId).and(MEDIA_ASSET.TENANT_ID.eq(tenantId)))
                .execute();
    }

    private Asset mapAsset(Record r) {
        Boolean cp = r.get("contains_pii", Boolean.class);
        Boolean ag = r.get("ai_generated", Boolean.class);
        return new Asset(
                r.get(MEDIA_ASSET.ID, String.class),
                r.get(MEDIA_ASSET.TENANT_ID, String.class),
                r.get(MEDIA_ASSET.PROJECT_ID, String.class),
                r.get(MEDIA_ASSET.STORAGE_KEY, String.class),
                r.get(MEDIA_ASSET.MEDIA_TYPE, String.class),
                r.get(MEDIA_ASSET.FILENAME, String.class),
                r.get(MEDIA_ASSET.SIZE_BYTES, Long.class),
                r.get(MEDIA_ASSET.CHECKSUM, String.class),
                r.get(MEDIA_ASSET.MEDIA_VERSION, String.class),
                r.get(MEDIA_ASSET.OWNER_ID, String.class),
                r.get(MEDIA_ASSET.ENTITY_REF, String.class),
                r.get(MEDIA_ASSET.CLASSIFICATION, String.class),
                r.get(MEDIA_ASSET.LICENSE, String.class),
                r.get(MEDIA_ASSET.RETENTION_POLICY, String.class),
                r.get(MEDIA_ASSET.SECURITY_LEVEL, String.class),
                Boolean.TRUE.equals(cp),
                Boolean.TRUE.equals(ag),
                r.get(MEDIA_ASSET.PUBLISH_STATUS, String.class),
                r.get(MEDIA_ASSET.CREATED_AT, LocalDateTime.class) != null
                        ? r.get(MEDIA_ASSET.CREATED_AT, LocalDateTime.class).toInstant(ZoneOffset.UTC) : null,
                r.get(MEDIA_ASSET.UPDATED_AT, LocalDateTime.class) != null
                        ? r.get(MEDIA_ASSET.UPDATED_AT, LocalDateTime.class).toInstant(ZoneOffset.UTC) : null
        );
    }
}

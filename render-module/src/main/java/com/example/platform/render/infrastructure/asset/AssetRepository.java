package com.example.platform.render.infrastructure.asset;

import com.example.platform.render.domain.asset.Asset;
import com.example.platform.shared.Ids;
import com.example.platform.storage.contract.StorageKeyPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.Asset.ASSET;


/**
 * Repository for project assets.
 *
 * <p>All storage keys are validated via {@link StorageKeyPolicy} before persistence.
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
                          String checksum, Long durationMs, Integer width, Integer height) {
        // Validate storage key via policy
        StorageKeyPolicy.assertValidPath(storageKey);

        String id = Ids.newId("asset");
        Instant now = Instant.now();

        dsl.insertInto(ASSET)
                .columns(
                        ASSET.ID,
                        ASSET.TENANT_ID,
                        ASSET.PROJECT_ID,
                        ASSET.STORAGE_KEY,
                        ASSET.MEDIA_TYPE,
                        ASSET.FILENAME,
                        ASSET.SIZE_BYTES,
                        ASSET.CHECKSUM,
                        ASSET.DURATION_MS,
                        ASSET.WIDTH,
                        ASSET.HEIGHT,
                        ASSET.ASSET_VERSION,
                        ASSET.OWNER_ID,
                        ASSET.ENTITY_REF,
                        ASSET.CLASSIFICATION,
                        ASSET.LICENSE,
                        ASSET.RETENTION_POLICY,
                        ASSET.SECURITY_LEVEL,
                        ASSET.CONTAINS_PII,
                        ASSET.AI_GENERATED,
                        ASSET.CREATED_AT,
                        ASSET.UPDATED_AT,
                        ASSET.PUBLISH_STATUS
                )
                .values(
                        id,
                        tenantId,
                        projectId,
                        storageKey,
                        mediaType,
                        filename,
                        sizeBytes,
                        checksum,
                        durationMs,
                        width,
                        height,
                        "v1",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        now,
                        now,
                        "DRAFT"
                )
                .execute();

        return new Asset(id, tenantId, projectId, storageKey, mediaType, filename,
                sizeBytes, checksum, durationMs, width, height,
                "v1", null, null, null, null, null, null, false, false, "DRAFT", now, now);
    }

    /**
     * Find an asset by ID, scoped to tenant.
     */
    public Optional<Asset> findById(String tenantId, String assetId) {
        Record r = dsl.selectFrom(ASSET)
                .where(ASSET.ID.eq(assetId))
                .and(ASSET.TENANT_ID.eq(tenantId))
                .fetchOne();
        return Optional.ofNullable(r).map(this::mapAsset);
    }

    /**
     * List all assets for a project, scoped to tenant.
     */
    public List<Asset> listByProject(String tenantId, String projectId) {
        return dsl.selectFrom(ASSET)
                .where(ASSET.TENANT_ID.eq(tenantId))
                .and(ASSET.PROJECT_ID.eq(projectId))
                .orderBy(ASSET.CREATED_AT.desc())
                .fetch(this::mapAsset);
    }

    /**
     * Delete an asset by ID, scoped to tenant.
     */
    public boolean delete(String tenantId, String assetId) {
        return dsl.deleteFrom(ASSET)
                .where(ASSET.ID.eq(assetId).and(ASSET.TENANT_ID.eq(tenantId)))
                .execute() > 0;
    }

    public void updatePublishStatus(String tenantId, String assetId, String publishStatus) {
        dsl.update(ASSET)
                .set(ASSET.PUBLISH_STATUS, publishStatus)
                .where(ASSET.ID.eq(assetId).and(ASSET.TENANT_ID.eq(tenantId)))
                .execute();
    }

    private Asset mapAsset(Record r) {
        Boolean cp = r.get("contains_pii", Boolean.class);
        Boolean ag = r.get("ai_generated", Boolean.class);
        return new Asset(
                r.get(ASSET.ID, String.class),
                r.get(ASSET.TENANT_ID, String.class),
                r.get(ASSET.PROJECT_ID, String.class),
                r.get(ASSET.STORAGE_KEY, String.class),
                r.get(ASSET.MEDIA_TYPE, String.class),
                r.get(ASSET.FILENAME, String.class),
                r.get(ASSET.SIZE_BYTES, Long.class),
                r.get(ASSET.CHECKSUM, String.class),
                r.get(ASSET.DURATION_MS, Long.class),
                r.get(ASSET.WIDTH, Integer.class),
                r.get(ASSET.HEIGHT, Integer.class),
                r.get(ASSET.ASSET_VERSION, String.class),
                r.get(ASSET.OWNER_ID, String.class),
                r.get(ASSET.ENTITY_REF, String.class),
                r.get(ASSET.CLASSIFICATION, String.class),
                r.get(ASSET.LICENSE, String.class),
                r.get(ASSET.RETENTION_POLICY, String.class),
                r.get(ASSET.SECURITY_LEVEL, String.class),
                Boolean.TRUE.equals(cp),
                Boolean.TRUE.equals(ag),
                r.get(ASSET.PUBLISH_STATUS, String.class),
                r.get(ASSET.CREATED_AT, java.time.Instant.class),
                r.get(ASSET.UPDATED_AT, java.time.Instant.class)
        );
    }
}

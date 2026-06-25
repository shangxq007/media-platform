package com.example.platform.render.infrastructure.asset;

import com.example.platform.render.domain.asset.Asset;
import com.example.platform.shared.Ids;
import com.example.platform.shared.tenant.StorageKeyPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

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

        dsl.insertInto(table("asset"))
                .columns(
                        field("id"),
                        field("tenant_id"),
                        field("project_id"),
                        field("storage_key"),
                        field("media_type"),
                        field("filename"),
                        field("size_bytes"),
                        field("checksum"),
                        field("duration_ms"),
                        field("width"),
                        field("height"),
                        field("asset_version"),
                        field("owner_id"),
                        field("entity_ref"),
                        field("classification"),
                        field("license"),
                        field("retention_policy"),
                        field("security_level"),
                        field("contains_pii"),
                        field("ai_generated"),
                        field("created_at"),
                        field("updated_at"),
                        field("publish_status")
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
        Record r = dsl.selectFrom(table("asset"))
                .where(field("id").eq(assetId))
                .and(field("tenant_id").eq(tenantId))
                .fetchOne();
        return Optional.ofNullable(r).map(this::mapAsset);
    }

    /**
     * List all assets for a project, scoped to tenant.
     */
    public List<Asset> listByProject(String tenantId, String projectId) {
        return dsl.selectFrom(table("asset"))
                .where(field("tenant_id").eq(tenantId))
                .and(field("project_id").eq(projectId))
                .orderBy(field("created_at").desc())
                .fetch(this::mapAsset);
    }

    /**
     * Delete an asset by ID, scoped to tenant.
     */
    public boolean delete(String tenantId, String assetId) {
        return dsl.deleteFrom(table("asset"))
                .where(field("id").eq(assetId).and(field("tenant_id").eq(tenantId)))
                .execute() > 0;
    }

    public void updatePublishStatus(String tenantId, String assetId, String publishStatus) {
        dsl.update(table("asset"))
                .set(field("publish_status"), publishStatus)
                .where(field("id").eq(assetId).and(field("tenant_id").eq(tenantId)))
                .execute();
    }

    private Asset mapAsset(Record r) {
        Boolean cp = r.get("contains_pii", Boolean.class);
        Boolean ag = r.get("ai_generated", Boolean.class);
        return new Asset(
                r.get(field("id"), String.class),
                r.get(field("tenant_id"), String.class),
                r.get(field("project_id"), String.class),
                r.get(field("storage_key"), String.class),
                r.get(field("media_type"), String.class),
                r.get(field("filename"), String.class),
                r.get(field("size_bytes"), Long.class),
                r.get(field("checksum"), String.class),
                r.get(field("duration_ms"), Long.class),
                r.get(field("width"), Integer.class),
                r.get(field("height"), Integer.class),
                r.get(field("asset_version"), String.class),
                r.get(field("owner_id"), String.class),
                r.get(field("entity_ref"), String.class),
                r.get(field("classification"), String.class),
                r.get(field("license"), String.class),
                r.get(field("retention_policy"), String.class),
                r.get(field("security_level"), String.class),
                Boolean.TRUE.equals(cp),
                Boolean.TRUE.equals(ag),
                r.get(field("publish_status"), String.class),
                r.get(field("created_at"), java.time.Instant.class),
                r.get(field("updated_at"), java.time.Instant.class)
        );
    }
}

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
import java.util.List;
import org.jooq.Record;
import com.example.platform.media.api.Asset;
import com.example.platform.storage.contract.StorageKeyPolicy;
import com.example.platform.shared.web.TenantGuard;
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
    /**
     * Register a new asset.
     */
    public Asset register(String tenantId, String projectId, String storageKey,
                          String mediaType, String filename, Long sizeBytes,
                          String checksum) {
        TenantGuard.assertSameTenant(tenantId);
        if (projectId == null || projectId.isBlank()) throw new IllegalArgumentException("project required");
        // Validate storage reference projection via Storage owner policy
        StorageKeyPolicy.assertValidPath(storageKey);

        String id = ("asset_" + java.util.UUID.randomUUID().toString().replace("-", ""));
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
        TenantGuard.assertSameTenant(tenantId);
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
        TenantGuard.assertSameTenant(tenantId);
        return dsl.selectFrom(MEDIA_ASSET)
                .where(MEDIA_ASSET.TENANT_ID.eq(tenantId))
                .and(MEDIA_ASSET.PROJECT_ID.eq(projectId))
                .orderBy(MEDIA_ASSET.CREATED_AT.desc())
                .fetch(this::mapAsset);
    }

    /**
     * Delete an asset by ID, scoped to tenant.
     */
    public boolean delete(String tenantId, String projectId, String assetId, String expectedVersion) {
        TenantGuard.assertSameTenant(tenantId);
        return dsl.deleteFrom(MEDIA_ASSET)
                .where(MEDIA_ASSET.ID.eq(assetId).and(MEDIA_ASSET.TENANT_ID.eq(tenantId))
                    .and(MEDIA_ASSET.PROJECT_ID.eq(projectId)).and(MEDIA_ASSET.MEDIA_VERSION.eq(expectedVersion)))
                .execute() > 0;
    }

    public Asset publicationSnapshot(String tenant, String project, String asset) {
        TenantGuard.assertSameTenant(tenant);
        var row=dsl.selectFrom(MEDIA_ASSET).where(MEDIA_ASSET.ID.eq(asset))
                .and(MEDIA_ASSET.TENANT_ID.eq(tenant)).and(MEDIA_ASSET.PROJECT_ID.eq(project)).forShare().fetchOne();
        if(row==null) throw new IllegalArgumentException("Media subject unavailable in scope");
        return mapAsset(row);
    }

    public boolean archivePublicationIfCurrent(String tenantId, String projectId, String assetId, String expectedVersion) {
        TenantGuard.assertSameTenant(tenantId);
        if (expectedVersion == null || expectedVersion.isBlank()) throw new IllegalArgumentException("Media version required");
        // PostgreSQL rechecks these predicates after a concurrent row writer commits.
        // No read/check/write gap, and no new version is adopted on a conditional miss.
        return dsl.update(MEDIA_ASSET).set(MEDIA_ASSET.PUBLISH_STATUS, "ARCHIVED")
                .where(MEDIA_ASSET.ID.eq(assetId).and(MEDIA_ASSET.TENANT_ID.eq(tenantId))
                    .and(MEDIA_ASSET.PROJECT_ID.eq(projectId)).and(MEDIA_ASSET.MEDIA_VERSION.eq(expectedVersion)))
                .execute() == 1;
    }

    public void updatePublishStatus(String tenantId, String projectId, String assetId, String expectedStatus, String publishStatus) {
        TenantGuard.assertSameTenant(tenantId);
        if (!java.util.Set.of("PUBLISHED", "ARCHIVED").contains(publishStatus)) throw new IllegalArgumentException("unsupported publication outcome");
        int changed = dsl.update(MEDIA_ASSET)
                .set(MEDIA_ASSET.PUBLISH_STATUS, publishStatus)
                .where(MEDIA_ASSET.ID.eq(assetId).and(MEDIA_ASSET.TENANT_ID.eq(tenantId))
                    .and(MEDIA_ASSET.PROJECT_ID.eq(projectId)).and(MEDIA_ASSET.PUBLISH_STATUS.eq(expectedStatus)))
                .execute();
        if (changed != 1) throw new IllegalStateException("asset scope or publication status changed");
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

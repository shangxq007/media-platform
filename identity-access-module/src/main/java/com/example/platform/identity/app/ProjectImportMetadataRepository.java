package com.example.platform.identity.app;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import static com.example.platform.typedschema.jooq.generated.tables.ProjectImportMetadata.PROJECT_IMPORT_METADATA;


/**
 * Repository for project_import_metadata table.
 *
 * <p>Stores JSON metadata from project-export-v1.zip imports.
 * All sensitive URLs are scrubbed before storage.
 */
@Repository
public class ProjectImportMetadataRepository {

    private final DSLContext dsl;

    public ProjectImportMetadataRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(MetadataRecord record) {
        dsl.insertInto(PROJECT_IMPORT_METADATA)
                .columns(
                        PROJECT_IMPORT_METADATA.ID,
                        PROJECT_IMPORT_METADATA.TENANT_ID,
                        PROJECT_IMPORT_METADATA.PROJECT_ID,
                        PROJECT_IMPORT_METADATA.IMPORT_ID,
                        PROJECT_IMPORT_METADATA.SOURCE_PROJECT_ID,
                        PROJECT_IMPORT_METADATA.SOURCE_EXPORT_ID,
                        PROJECT_IMPORT_METADATA.SCHEMA_VERSION,
                        PROJECT_IMPORT_METADATA.TIMELINE_JSON,
                        PROJECT_IMPORT_METADATA.TIMELINE_OTIO_JSON,
                        PROJECT_IMPORT_METADATA.RENDER_PLAN_JSON,
                        PROJECT_IMPORT_METADATA.SPATIAL_PLAN_JSON,
                        PROJECT_IMPORT_METADATA.EXPORT_PROFILES_JSON,
                        PROJECT_IMPORT_METADATA.EFFECT_TAXONOMY_JSON,
                        PROJECT_IMPORT_METADATA.APPLIED_EFFECTS_JSON,
                        PROJECT_IMPORT_METADATA.ASSET_MAPPING_JSON,
                        PROJECT_IMPORT_METADATA.CREATED_AT)
                .values(
                        record.id(),
                        record.tenantId(),
                        record.projectId(),
                        record.importId(),
                        record.sourceProjectId(),
                        record.sourceExportId(),
                        record.schemaVersion(),
                        record.timelineJson(),
                        record.timelineOtioJson(),
                        record.renderPlanJson(),
                        record.spatialPlanJson(),
                        record.exportProfilesJson(),
                        record.effectTaxonomyJson(),
                        record.appliedEffectsJson(),
                        record.assetMappingJson(),
                        LocalDateTime.ofInstant(record.createdAt(), ZoneOffset.UTC))
                .execute();
    }

    public Optional<MetadataRecord> findByImportId(String importId) {
        Record record = dsl.select()
                .from(PROJECT_IMPORT_METADATA)
                .where(PROJECT_IMPORT_METADATA.IMPORT_ID.eq(importId))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public Optional<MetadataRecord> findByProjectId(String projectId, String tenantId) {
        Record record = dsl.select()
                .from(PROJECT_IMPORT_METADATA)
                .where(PROJECT_IMPORT_METADATA.PROJECT_ID.eq(projectId))
                .and(PROJECT_IMPORT_METADATA.TENANT_ID.eq(tenantId))
                .orderBy(PROJECT_IMPORT_METADATA.CREATED_AT.desc())
                .limit(1)
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<MetadataRecord> listByTenantId(String tenantId, int limit) {
        return dsl.select()
                .from(PROJECT_IMPORT_METADATA)
                .where(PROJECT_IMPORT_METADATA.TENANT_ID.eq(tenantId))
                .orderBy(PROJECT_IMPORT_METADATA.CREATED_AT.desc())
                .limit(Math.max(1, Math.min(limit, 100)))
                .fetch()
                .map(this::mapRecord);
    }

    public boolean deleteByImportId(String importId) {
        int deleted = dsl.deleteFrom(PROJECT_IMPORT_METADATA)
                .where(PROJECT_IMPORT_METADATA.IMPORT_ID.eq(importId))
                .execute();
        return deleted > 0;
    }

    public boolean deleteByProjectId(String projectId, String tenantId) {
        int deleted = dsl.deleteFrom(PROJECT_IMPORT_METADATA)
                .where(PROJECT_IMPORT_METADATA.PROJECT_ID.eq(projectId))
                .and(PROJECT_IMPORT_METADATA.TENANT_ID.eq(tenantId))
                .execute();
        return deleted > 0;
    }

    private MetadataRecord mapRecord(Record record) {
        return new MetadataRecord(
                record.get(PROJECT_IMPORT_METADATA.ID),
                record.get(PROJECT_IMPORT_METADATA.TENANT_ID),
                record.get(PROJECT_IMPORT_METADATA.PROJECT_ID),
                record.get(PROJECT_IMPORT_METADATA.IMPORT_ID),
                record.get(PROJECT_IMPORT_METADATA.SOURCE_PROJECT_ID),
                record.get(PROJECT_IMPORT_METADATA.SOURCE_EXPORT_ID),
                record.get(PROJECT_IMPORT_METADATA.SCHEMA_VERSION),
                record.get(PROJECT_IMPORT_METADATA.TIMELINE_JSON),
                record.get(PROJECT_IMPORT_METADATA.TIMELINE_OTIO_JSON),
                record.get(PROJECT_IMPORT_METADATA.RENDER_PLAN_JSON),
                record.get(PROJECT_IMPORT_METADATA.SPATIAL_PLAN_JSON),
                record.get(PROJECT_IMPORT_METADATA.EXPORT_PROFILES_JSON),
                record.get(PROJECT_IMPORT_METADATA.EFFECT_TAXONOMY_JSON),
                record.get(PROJECT_IMPORT_METADATA.APPLIED_EFFECTS_JSON),
                record.get(PROJECT_IMPORT_METADATA.ASSET_MAPPING_JSON),
                record.get(PROJECT_IMPORT_METADATA.CREATED_AT, LocalDateTime.class).toInstant(ZoneOffset.UTC)
        );
    }

    /**
     * Record for project_import_metadata table.
     */
    public record MetadataRecord(
            String id,
            String tenantId,
            String projectId,
            String importId,
            String sourceProjectId,
            String sourceExportId,
            String schemaVersion,
            String timelineJson,
            String timelineOtioJson,
            String renderPlanJson,
            String spatialPlanJson,
            String exportProfilesJson,
            String effectTaxonomyJson,
            String appliedEffectsJson,
            String assetMappingJson,
            java.time.Instant createdAt
    ) {}
}

package com.example.platform.identity.app;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

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
        dsl.insertInto(table("project_import_metadata"))
                .columns(
                        field("id"),
                        field("tenant_id"),
                        field("project_id"),
                        field("import_id"),
                        field("source_project_id"),
                        field("source_export_id"),
                        field("schema_version"),
                        field("timeline_json"),
                        field("timeline_otio_json"),
                        field("render_plan_json"),
                        field("spatial_plan_json"),
                        field("export_profiles_json"),
                        field("effect_taxonomy_json"),
                        field("applied_effects_json"),
                        field("asset_mapping_json"),
                        field("created_at"))
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
                        record.createdAt())
                .execute();
    }

    public Optional<MetadataRecord> findByImportId(String importId) {
        Record record = dsl.select()
                .from(table("project_import_metadata"))
                .where(field("import_id").eq(importId))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public Optional<MetadataRecord> findByProjectId(String projectId, String tenantId) {
        Record record = dsl.select()
                .from(table("project_import_metadata"))
                .where(field("project_id").eq(projectId))
                .and(field("tenant_id").eq(tenantId))
                .orderBy(field("created_at").desc())
                .limit(1)
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<MetadataRecord> listByTenantId(String tenantId, int limit) {
        return dsl.select()
                .from(table("project_import_metadata"))
                .where(field("tenant_id").eq(tenantId))
                .orderBy(field("created_at").desc())
                .limit(Math.max(1, Math.min(limit, 100)))
                .fetch()
                .map(this::mapRecord);
    }

    public boolean deleteByImportId(String importId) {
        int deleted = dsl.deleteFrom(table("project_import_metadata"))
                .where(field("import_id").eq(importId))
                .execute();
        return deleted > 0;
    }

    public boolean deleteByProjectId(String projectId, String tenantId) {
        int deleted = dsl.deleteFrom(table("project_import_metadata"))
                .where(field("project_id").eq(projectId))
                .and(field("tenant_id").eq(tenantId))
                .execute();
        return deleted > 0;
    }

    private MetadataRecord mapRecord(Record record) {
        return new MetadataRecord(
                record.get(field("id", String.class)),
                record.get(field("tenant_id", String.class)),
                record.get(field("project_id", String.class)),
                record.get(field("import_id", String.class)),
                record.get(field("source_project_id", String.class)),
                record.get(field("source_export_id", String.class)),
                record.get(field("schema_version", String.class)),
                record.get(field("timeline_json", String.class)),
                record.get(field("timeline_otio_json", String.class)),
                record.get(field("render_plan_json", String.class)),
                record.get(field("spatial_plan_json", String.class)),
                record.get(field("export_profiles_json", String.class)),
                record.get(field("effect_taxonomy_json", String.class)),
                record.get(field("applied_effects_json", String.class)),
                record.get(field("asset_mapping_json", String.class)),
                record.get(field("created_at", OffsetDateTime.class)).toInstant()
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

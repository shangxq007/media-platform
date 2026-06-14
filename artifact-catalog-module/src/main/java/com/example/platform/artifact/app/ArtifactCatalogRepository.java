package com.example.platform.artifact.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;

/**
 * Persistence repository for {@link Artifact} entities in the artifact catalog.
 *
 * <p>Only created when a {@link DSLContext} bean is available (i.e., when the
 * datasource-module is properly configured). The {@link ArtifactCatalogService}
 * falls back to in-memory storage when this repository is not available.</p>
 *
 * <p><strong>Note:</strong> Uses lowercase column names for PostgreSQL compatibility.
 * The DSLContext should be configured with RenderNameCase.LOWER.</p>
 */
@Repository

public class ArtifactCatalogRepository {

    private final DSLContext dsl;

    public ArtifactCatalogRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Artifact save(Artifact artifact) {
        OffsetDateTime createdAt = artifact.createdAt() != null
                ? OffsetDateTime.ofInstant(artifact.createdAt(), ZoneOffset.UTC)
                : OffsetDateTime.now();
        String status = artifact.status() != null ? artifact.status().name() : ArtifactStatus.ACTIVE.name();
        dsl.insertInto(table("artifact"))
                .columns(field("id"), field("render_job_id"), field("project_id"),
                        field("storage_uri"), field("format"), field("resolution"),
                        field("duration"), field("status"), field("tombstoned_at"), field("created_at"))
                .values(artifact.id(), artifact.renderJobId(), artifact.projectId(),
                        artifact.storageUri(), artifact.format(), artifact.resolution(),
                        artifact.duration(), status,
                        artifact.tombstonedAt() != null
                                ? OffsetDateTime.ofInstant(artifact.tombstonedAt(), ZoneOffset.UTC)
                                : null,
                        createdAt)
                .execute();
        return artifact;
    }

    public Artifact updateStatus(String artifactId, ArtifactStatus status, Instant tombstonedAt) {
        OffsetDateTime tombstoneTs = tombstonedAt != null
                ? OffsetDateTime.ofInstant(tombstonedAt, ZoneOffset.UTC)
                : null;
        dsl.update(table("artifact"))
                .set(field("status"), status.name())
                .set(field("tombstoned_at"), tombstoneTs)
                .where(field("id").eq(artifactId))
                .execute();
        return findById(artifactId).orElseThrow();
    }

    public Optional<Artifact> findById(String id) {
        Record record = dsl.select()
                .from(table("artifact"))
                .where(field("id").eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<Artifact> findByProjectId(String projectId) {
        return dsl.select()
                .from(table("artifact"))
                .where(field("project_id").eq(projectId))
                .orderBy(field("created_at").desc())
                .fetch(this::mapRecord);
    }

    public List<Artifact> findByRenderJobId(String renderJobId) {
        return dsl.select()
                .from(table("artifact"))
                .where(field("render_job_id").eq(renderJobId))
                .orderBy(field("created_at").desc())
                .fetch(this::mapRecord);
    }

    public List<Artifact> findAll() {
        return dsl.select()
                .from(table("artifact"))
                .orderBy(field("created_at").desc())
                .fetch(this::mapRecord);
    }

    public List<Artifact> findTombstonedBefore(Instant cutoff) {
        OffsetDateTime cutoffTs = OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC);
        return dsl.select()
                .from(table("artifact"))
                .where(field("status").eq(ArtifactStatus.TOMBSTONED.name()))
                .and(field("tombstoned_at").isNotNull())
                .and(field("tombstoned_at").lessThan(cutoffTs))
                .orderBy(field("tombstoned_at").asc())
                .fetch(this::mapRecord);
    }

    private Artifact mapRecord(Record record) {
        OffsetDateTime createdAt = record.get(field("created_at"), OffsetDateTime.class);
        OffsetDateTime tombstonedAt = record.get(field("tombstoned_at"), OffsetDateTime.class);
        Long duration = record.get(field("duration"), Long.class);
        String statusRaw = record.get(field("status"), String.class);
        ArtifactStatus status = statusRaw != null && !statusRaw.isBlank()
                ? ArtifactStatus.valueOf(statusRaw)
                : ArtifactStatus.ACTIVE;
        return new Artifact(
                record.get(field("id"), String.class),
                record.get(field("render_job_id"), String.class),
                record.get(field("project_id"), String.class),
                record.get(field("storage_uri"), String.class),
                record.get(field("format"), String.class),
                record.get(field("resolution"), String.class),
                duration,
                null, // size_bytes not in schema
                null, // checksum not in schema
                status,
                tombstonedAt != null ? tombstonedAt.toInstant() : null,
                createdAt != null ? createdAt.toInstant() : null
        );
    }
}

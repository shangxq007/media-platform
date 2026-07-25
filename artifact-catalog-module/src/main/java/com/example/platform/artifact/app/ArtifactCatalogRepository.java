package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT;


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
        LocalDateTime createdAt = artifact.createdAt() != null
                ? LocalDateTime.ofInstant(artifact.createdAt(), ZoneOffset.UTC)
                : LocalDateTime.now();
        String status = artifact.status() != null ? artifact.status().name() : ArtifactStatus.ACTIVE.name();
        dsl.insertInto(ARTIFACT)
                .columns(ARTIFACT.ID, ARTIFACT.RENDER_JOB_ID, ARTIFACT.PROJECT_ID,
                        ARTIFACT.STORAGE_URI, ARTIFACT.FORMAT, ARTIFACT.RESOLUTION,
                        ARTIFACT.DURATION, ARTIFACT.STATUS, ARTIFACT.TOMBSTONED_AT, ARTIFACT.CREATED_AT)
                .values(artifact.id(), artifact.renderJobId(), artifact.projectId(),
                        artifact.storageUri(), artifact.format(), artifact.resolution(),
                        artifact.duration(), status,
                        artifact.tombstonedAt() != null
                                ? LocalDateTime.ofInstant(artifact.tombstonedAt(), ZoneOffset.UTC)
                                : null,
                        createdAt)
                .execute();
        return artifact;
    }

    public Artifact updateStatus(String artifactId, ArtifactStatus status, Instant tombstonedAt) {
        LocalDateTime tombstoneTs = tombstonedAt != null
                ? LocalDateTime.ofInstant(tombstonedAt, ZoneOffset.UTC)
                : null;
        dsl.update(ARTIFACT)
                .set(ARTIFACT.STATUS, status.name())
                .set(ARTIFACT.TOMBSTONED_AT, tombstoneTs)
                .where(ARTIFACT.ID.eq(artifactId))
                .execute();
        return findById(artifactId).orElseThrow();
    }

    public Optional<Artifact> findById(String id) {
        Record record = dsl.select()
                .from(ARTIFACT)
                .where(ARTIFACT.ID.eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<Artifact> findByProjectId(String projectId) {
        return dsl.select()
                .from(ARTIFACT)
                .where(ARTIFACT.PROJECT_ID.eq(projectId))
                .orderBy(ARTIFACT.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    public List<Artifact> findByRenderJobId(String renderJobId) {
        return dsl.select()
                .from(ARTIFACT)
                .where(ARTIFACT.RENDER_JOB_ID.eq(renderJobId))
                .orderBy(ARTIFACT.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    public List<Artifact> findAll() {
        return dsl.select()
                .from(ARTIFACT)
                .orderBy(ARTIFACT.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    public List<Artifact> findTombstonedBefore(Instant cutoff) {
        LocalDateTime cutoffTs = LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC);
        return dsl.select()
                .from(ARTIFACT)
                .where(ARTIFACT.STATUS.eq(ArtifactStatus.TOMBSTONED.name()))
                .and(ARTIFACT.TOMBSTONED_AT.isNotNull())
                .and(ARTIFACT.TOMBSTONED_AT.lessThan(cutoffTs))
                .orderBy(ARTIFACT.TOMBSTONED_AT.asc())
                .fetch(this::mapRecord);
    }

    private Artifact mapRecord(Record record) {
        LocalDateTime createdAt = record.get(ARTIFACT.CREATED_AT, LocalDateTime.class);
        LocalDateTime tombstonedAt = record.get(ARTIFACT.TOMBSTONED_AT, LocalDateTime.class);
        Long duration = record.get(ARTIFACT.DURATION, Long.class);
        String statusRaw = record.get(ARTIFACT.STATUS, String.class);
        ArtifactStatus status = statusRaw != null && !statusRaw.isBlank()
                ? ArtifactStatus.valueOf(statusRaw)
                : ArtifactStatus.ACTIVE;
        return new Artifact(
                record.get(ARTIFACT.ID, String.class),
                record.get(ARTIFACT.RENDER_JOB_ID, String.class),
                record.get(ARTIFACT.PROJECT_ID, String.class),
                record.get(ARTIFACT.STORAGE_URI, String.class),
                record.get(ARTIFACT.FORMAT, String.class),
                record.get(ARTIFACT.RESOLUTION, String.class),
                duration,
                null, // size_bytes not in schema
                null, // checksum not in schema
                status,
                tombstonedAt != null ? tombstonedAt.atZone(ZoneOffset.UTC).toInstant() : null,
                createdAt != null ? createdAt.atZone(ZoneOffset.UTC).toInstant() : null
        );
    }
}

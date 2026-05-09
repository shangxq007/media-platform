package com.example.platform.artifact.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.artifact.domain.Artifact;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

/**
 * Persistence repository for {@link Artifact} entities in the artifact catalog.
 *
 * <p>Only created when a {@link DSLContext} bean is available (i.e., when the
 * datasource-module is properly configured). The {@link ArtifactCatalogService}
 * falls back to in-memory storage when this repository is not available.</p>
 *
 * <p><strong>Note:</strong> H2 in PostgreSQL mode stores column names in uppercase.
 * We use uppercase field references for all operations.</p>
 */
@Repository
@ConditionalOnBean(DSLContext.class)
public class ArtifactCatalogRepository {

    private final DSLContext dsl;

    public ArtifactCatalogRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Artifact save(Artifact artifact) {
        OffsetDateTime createdAt = artifact.createdAt() != null
                ? OffsetDateTime.ofInstant(artifact.createdAt(), ZoneOffset.UTC)
                : OffsetDateTime.now();
        dsl.insertInto(table("ARTIFACT"))
                .columns(field("ID"), field("RENDER_JOB_ID"), field("PROJECT_ID"),
                        field("STORAGE_URI"), field("FORMAT"), field("RESOLUTION"),
                        field("DURATION"), field("CREATED_AT"))
                .values(artifact.id(), artifact.renderJobId(), artifact.projectId(),
                        artifact.storageUri(), artifact.format(), artifact.resolution(),
                        artifact.duration(), createdAt)
                .execute();
        return artifact;
    }

    public Optional<Artifact> findById(String id) {
        Record record = dsl.select()
                .from(table("ARTIFACT"))
                .where(field("ID").eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<Artifact> findByProjectId(String projectId) {
        return dsl.select()
                .from(table("ARTIFACT"))
                .where(field("PROJECT_ID").eq(projectId))
                .orderBy(field("CREATED_AT").desc())
                .fetch(this::mapRecord);
    }

    public List<Artifact> findByRenderJobId(String renderJobId) {
        return dsl.select()
                .from(table("ARTIFACT"))
                .where(field("RENDER_JOB_ID").eq(renderJobId))
                .orderBy(field("CREATED_AT").desc())
                .fetch(this::mapRecord);
    }

    public List<Artifact> findAll() {
        return dsl.select()
                .from(table("ARTIFACT"))
                .orderBy(field("CREATED_AT").desc())
                .fetch(this::mapRecord);
    }

    private Artifact mapRecord(Record record) {
        OffsetDateTime createdAt = record.get(field("CREATED_AT"), OffsetDateTime.class);
        Long duration = record.get(field("DURATION"), Long.class);
        return new Artifact(
                record.get(field("ID"), String.class),
                record.get(field("RENDER_JOB_ID"), String.class),
                record.get(field("PROJECT_ID"), String.class),
                record.get(field("STORAGE_URI"), String.class),
                record.get(field("FORMAT"), String.class),
                record.get(field("RESOLUTION"), String.class),
                duration,
                createdAt != null ? createdAt.toInstant() : null
        );
    }
}

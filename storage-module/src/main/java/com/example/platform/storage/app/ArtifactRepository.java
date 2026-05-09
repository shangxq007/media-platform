package com.example.platform.storage.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class ArtifactRepository {

    private final DSLContext dsl;

    public ArtifactRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public ArtifactMetadata save(ArtifactMetadata metadata) {
        dsl.insertInto(table("artifact"))
                .columns(field("id"), field("render_job_id"), field("project_id"),
                        field("storage_uri"), field("format"), field("resolution"),
                        field("duration"), field("created_at"))
                .values(metadata.id(), metadata.renderJobId(), metadata.projectId(),
                        metadata.storageUri(), metadata.format(), metadata.resolution(),
                        metadata.duration(), metadata.createdAt())
                .execute();
        return metadata;
    }

    public Optional<ArtifactMetadata> findById(String id) {
        Record record = dsl.select()
                .from(table("artifact"))
                .where(field("id").eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<ArtifactMetadata> findByRenderJobId(String renderJobId) {
        return dsl.select()
                .from(table("artifact"))
                .where(field("render_job_id").eq(renderJobId))
                .orderBy(field("created_at").desc())
                .fetch(this::mapRecord);
    }

    public List<ArtifactMetadata> findByProjectId(String projectId) {
        return dsl.select()
                .from(table("artifact"))
                .where(field("project_id").eq(projectId))
                .orderBy(field("created_at").desc())
                .fetch(this::mapRecord);
    }

    private ArtifactMetadata mapRecord(Record record) {
        return new ArtifactMetadata(
                record.get(field("id"), String.class),
                record.get(field("render_job_id"), String.class),
                record.get(field("project_id"), String.class),
                record.get(field("storage_uri"), String.class),
                record.get(field("format"), String.class),
                record.get(field("resolution"), String.class),
                record.get(field("duration"), Long.class),
                record.get(field("created_at"), OffsetDateTime.class).toInstant()
        );
    }

    public record ArtifactMetadata(
            String id,
            String renderJobId,
            String projectId,
            String storageUri,
            String format,
            String resolution,
            Long duration,
            java.time.Instant createdAt) {}
}

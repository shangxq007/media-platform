package com.example.platform.storage.app;

import static com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
        dsl.insertInto(ARTIFACT)
                .columns(ARTIFACT.ID, ARTIFACT.RENDER_JOB_ID, ARTIFACT.PROJECT_ID,
                        ARTIFACT.STORAGE_URI, ARTIFACT.FORMAT, ARTIFACT.RESOLUTION,
                        ARTIFACT.DURATION, ARTIFACT.CREATED_AT)
                .values(metadata.id(), metadata.renderJobId(), metadata.projectId(),
                        metadata.storageUri(), metadata.format(), metadata.resolution(),
                        metadata.duration(),
                        LocalDateTime.ofInstant(metadata.createdAt(), ZoneOffset.UTC))
                .execute();
        return metadata;
    }

    public Optional<ArtifactMetadata> findById(String id) {
        Record record = dsl.select()
                .from(ARTIFACT)
                .where(ARTIFACT.ID.eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<ArtifactMetadata> findByRenderJobId(String renderJobId) {
        return dsl.select()
                .from(ARTIFACT)
                .where(ARTIFACT.RENDER_JOB_ID.eq(renderJobId))
                .orderBy(ARTIFACT.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    public List<ArtifactMetadata> findByProjectId(String projectId) {
        return dsl.select()
                .from(ARTIFACT)
                .where(ARTIFACT.PROJECT_ID.eq(projectId))
                .orderBy(ARTIFACT.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    private ArtifactMetadata mapRecord(Record record) {
        return new ArtifactMetadata(
                record.get(ARTIFACT.ID),
                record.get(ARTIFACT.RENDER_JOB_ID),
                record.get(ARTIFACT.PROJECT_ID),
                record.get(ARTIFACT.STORAGE_URI),
                record.get(ARTIFACT.FORMAT),
                record.get(ARTIFACT.RESOLUTION),
                record.get(ARTIFACT.DURATION),
                record.get(ARTIFACT.CREATED_AT).toInstant(ZoneOffset.UTC)
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

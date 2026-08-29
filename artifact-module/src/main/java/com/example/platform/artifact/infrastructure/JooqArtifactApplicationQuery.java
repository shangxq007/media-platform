package com.example.platform.artifact.infrastructure;

import static com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT;

import com.example.platform.artifact.app.ArtifactApplicationQuery;
import com.example.platform.artifact.app.ArtifactScope;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/** Database-scoped implementation; never performs a process-wide enumeration. */
@Repository
public class JooqArtifactApplicationQuery implements ArtifactApplicationQuery {

    private final DSLContext dsl;

    public JooqArtifactApplicationQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<Artifact> findArtifacts(ArtifactScope scope, int limit) {
        return dsl.selectFrom(ARTIFACT)
                .where(ARTIFACT.TENANT_ID.eq(scope.tenantId())
                        .and(ARTIFACT.PROJECT_ID.eq(scope.projectId()))
                        .and(ARTIFACT.RENDER_JOB_ID.eq(scope.renderJobId())))
                .orderBy(ARTIFACT.CREATED_AT.desc(), ARTIFACT.ID.asc())
                .limit(limit)
                .fetch(this::toArtifact);
    }

    @Override
    public long countArtifacts(ArtifactScope scope) {
        return dsl.fetchCount(ARTIFACT, ARTIFACT.TENANT_ID.eq(scope.tenantId())
                .and(ARTIFACT.PROJECT_ID.eq(scope.projectId()))
                .and(ARTIFACT.RENDER_JOB_ID.eq(scope.renderJobId())));
    }

    @Override
    public Optional<Artifact> findArtifact(ArtifactScope scope, ArtifactId artifactId) {
        return dsl.selectFrom(ARTIFACT)
                .where(ARTIFACT.ID.eq(artifactId.value())
                        .and(ARTIFACT.TENANT_ID.eq(scope.tenantId()))
                        .and(ARTIFACT.PROJECT_ID.eq(scope.projectId()))
                        .and(ARTIFACT.RENDER_JOB_ID.eq(scope.renderJobId())))
                .fetchOptional()
                .map(this::toArtifact);
    }

    private Artifact toArtifact(Record record) {
        return new Artifact(
                new ArtifactId(record.get(ARTIFACT.ID)),
                record.get(ARTIFACT.TENANT_ID),
                ContentDigest.sha256(record.get(ARTIFACT.CONTENT_DIGEST)),
                record.get(ARTIFACT.BYTE_LENGTH),
                ArtifactMediaType.valueOf(record.get(ARTIFACT.MEDIA_TYPE)),
                ArtifactKind.valueOf(record.get(ARTIFACT.ARTIFACT_KIND)),
                ArtifactState.valueOf(record.get(ARTIFACT.STATE)),
                record.get(ARTIFACT.SCHEMA_VERSION),
                record.get(ARTIFACT.CREATED_AT).toInstant(ZoneOffset.UTC));
    }
}

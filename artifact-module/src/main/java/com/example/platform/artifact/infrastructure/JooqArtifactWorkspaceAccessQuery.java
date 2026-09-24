package com.example.platform.artifact.infrastructure;

import static com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT;
import com.example.platform.artifact.app.ArtifactWorkspaceAccessQuery;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** Workspace access is established by the owning artifact relation: project_id is the workspace scope. */
@Repository
public class JooqArtifactWorkspaceAccessQuery implements ArtifactWorkspaceAccessQuery {
    private final DSLContext dsl;
    public JooqArtifactWorkspaceAccessQuery(DSLContext dsl) { this.dsl = dsl; }
    @Override public Optional<Artifact> findArtifact(String tenantId, String workspaceId, ArtifactId artifactId) {
        return dsl.selectFrom(ARTIFACT).where(ARTIFACT.ID.eq(artifactId.value())
                .and(ARTIFACT.TENANT_ID.eq(tenantId)).and(ARTIFACT.PROJECT_ID.eq(workspaceId)))
                .fetchOptional(r -> new Artifact(new ArtifactId(r.get(ARTIFACT.ID)), r.get(ARTIFACT.TENANT_ID),
                        ContentDigest.sha256(r.get(ARTIFACT.CONTENT_DIGEST)), r.get(ARTIFACT.BYTE_LENGTH),
                        ArtifactMediaType.valueOf(r.get(ARTIFACT.MEDIA_TYPE)), ArtifactKind.valueOf(r.get(ARTIFACT.ARTIFACT_KIND)),
                        ArtifactState.valueOf(r.get(ARTIFACT.STATE)), r.get(ARTIFACT.SCHEMA_VERSION), r.get(ARTIFACT.CREATED_AT).toInstant(ZoneOffset.UTC)));
    }
}

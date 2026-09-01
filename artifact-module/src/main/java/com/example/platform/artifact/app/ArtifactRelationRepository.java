package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.ArtifactRelation;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT;
import static com.example.platform.typedschema.jooq.generated.tables.ArtifactRelation.ARTIFACT_RELATION;


@Repository

public class ArtifactRelationRepository {

    private final DSLContext dsl;

    public ArtifactRelationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public ArtifactRelation save(ArtifactRelation relation) {
        dsl.insertInto(ARTIFACT_RELATION)
                .columns(ARTIFACT_RELATION.ID, ARTIFACT_RELATION.SOURCE_ARTIFACT_ID, ARTIFACT_RELATION.TARGET_ARTIFACT_ID,
                        ARTIFACT_RELATION.RELATION_TYPE, ARTIFACT_RELATION.CREATED_AT)
                .values(relation.id(), relation.sourceId(), relation.targetId(),
                        relation.relationType(), LocalDateTime.now(ZoneOffset.UTC))
                .execute();
        return relation;
    }

    /**
     * GCR2-CORRECTION-V1 (ARTIFACT_QUERY_TENANT_ARGUMENT_IS_SEMANTIC_NOT_DECORATIVE_V1):
     * tenant-scoped relation lookup. The root Artifact must belong to the requested
     * tenant AND both relation peers must belong to the same tenant
     * (ARTIFACT_PROVENANCE_TRAVERSAL_NEVER_CROSSES_TENANT_BOUNDARY_V1). Scoped via
     * canonical Artifact ownership — no tenant column added to artifact_relation.
     */
    public List<ArtifactRelation> findByArtifactIdScopedToTenant(String tenantId, String artifactId) {
        requireTenantId(tenantId);
        var sourceArtifact = ARTIFACT.as("sa");
        var targetArtifact = ARTIFACT.as("ta");
        return dsl.select(ARTIFACT_RELATION.ID, ARTIFACT_RELATION.SOURCE_ARTIFACT_ID,
                        ARTIFACT_RELATION.TARGET_ARTIFACT_ID, ARTIFACT_RELATION.RELATION_TYPE)
                .from(ARTIFACT_RELATION)
                .join(sourceArtifact).on(sourceArtifact.ID.eq(ARTIFACT_RELATION.SOURCE_ARTIFACT_ID))
                .join(targetArtifact).on(targetArtifact.ID.eq(ARTIFACT_RELATION.TARGET_ARTIFACT_ID))
                .where((ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq(artifactId)
                                .or(ARTIFACT_RELATION.TARGET_ARTIFACT_ID.eq(artifactId)))
                        .and(sourceArtifact.TENANT_ID.eq(tenantId))
                        .and(targetArtifact.TENANT_ID.eq(tenantId)))
                .fetch(this::mapRecord);
    }

    /** Whether a relation exists for the artifact with BOTH peers in the tenant. */
    public boolean hasRelationInTenant(String tenantId, String artifactId) {
        requireTenantId(tenantId);
        var sourceArtifact = ARTIFACT.as("sa");
        var targetArtifact = ARTIFACT.as("ta");
        return dsl.fetchExists(dsl.selectOne()
                .from(ARTIFACT_RELATION)
                .join(sourceArtifact).on(sourceArtifact.ID.eq(ARTIFACT_RELATION.SOURCE_ARTIFACT_ID))
                .join(targetArtifact).on(targetArtifact.ID.eq(ARTIFACT_RELATION.TARGET_ARTIFACT_ID))
                .where((ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq(artifactId)
                                .or(ARTIFACT_RELATION.TARGET_ARTIFACT_ID.eq(artifactId)))
                        .and(sourceArtifact.TENANT_ID.eq(tenantId))
                        .and(targetArtifact.TENANT_ID.eq(tenantId))));
    }

    public List<Map<String, Object>> findReferenceMapsScopedToTenant(String tenantId, String artifactId) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (ArtifactRelation relation : findByArtifactIdScopedToTenant(tenantId, artifactId)) {
            if (artifactId.equals(relation.sourceId())) {
                refs.add(Map.of(
                        "kind", "artifact_relation",
                        "relationId", relation.id(),
                        "role", "source",
                        "peerId", relation.targetId(),
                        "relationType", relation.relationType()));
            }
            if (artifactId.equals(relation.targetId())) {
                refs.add(Map.of(
                        "kind", "artifact_relation",
                        "relationId", relation.id(),
                        "role", "target",
                        "peerId", relation.sourceId(),
                        "relationType", relation.relationType()));
            }
        }
        return refs;
    }

    private static void requireTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank() || "*".equals(tenantId)) {
            throw new IllegalArgumentException("explicit tenantId is required");
        }
    }

    private ArtifactRelation mapRecord(Record record) {
        return new ArtifactRelation(
                record.get(ARTIFACT_RELATION.ID, String.class),
                record.get(ARTIFACT_RELATION.SOURCE_ARTIFACT_ID, String.class),
                record.get(ARTIFACT_RELATION.TARGET_ARTIFACT_ID, String.class),
                record.get(ARTIFACT_RELATION.RELATION_TYPE, String.class));
    }
}

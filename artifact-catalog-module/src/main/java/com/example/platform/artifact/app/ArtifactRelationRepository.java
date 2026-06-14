package com.example.platform.artifact.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.artifact.domain.ArtifactRelation;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;

@Repository

public class ArtifactRelationRepository {

    private final DSLContext dsl;

    public ArtifactRelationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public ArtifactRelation save(ArtifactRelation relation) {
        dsl.insertInto(table("artifact_relation"))
                .columns(field("id"), field("source_artifact_id"), field("target_artifact_id"),
                        field("relation_type"), field("created_at"))
                .values(relation.id(), relation.sourceId(), relation.targetId(),
                        relation.relationType(), OffsetDateTime.now(ZoneOffset.UTC))
                .execute();
        return relation;
    }

    public List<ArtifactRelation> findByArtifactId(String artifactId) {
        return dsl.select()
                .from(table("artifact_relation"))
                .where(field("source_artifact_id").eq(artifactId)
                        .or(field("target_artifact_id").eq(artifactId)))
                .fetch(this::mapRecord);
    }

    public List<Map<String, Object>> findReferenceMaps(String artifactId) {
        List<Map<String, Object>> refs = new ArrayList<>();
        for (ArtifactRelation relation : findByArtifactId(artifactId)) {
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

    private ArtifactRelation mapRecord(Record record) {
        return new ArtifactRelation(
                record.get(field("id"), String.class),
                record.get(field("source_artifact_id"), String.class),
                record.get(field("target_artifact_id"), String.class),
                record.get(field("relation_type"), String.class));
    }
}

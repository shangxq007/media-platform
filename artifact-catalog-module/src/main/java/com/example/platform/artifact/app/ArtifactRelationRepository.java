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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(DSLContext.class)
public class ArtifactRelationRepository {

    private final DSLContext dsl;

    public ArtifactRelationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public ArtifactRelation save(ArtifactRelation relation) {
        dsl.insertInto(table("ARTIFACT_RELATION"))
                .columns(field("ID"), field("SOURCE_ARTIFACT_ID"), field("TARGET_ARTIFACT_ID"),
                        field("RELATION_TYPE"), field("CREATED_AT"))
                .values(relation.id(), relation.sourceId(), relation.targetId(),
                        relation.relationType(), OffsetDateTime.now(ZoneOffset.UTC))
                .execute();
        return relation;
    }

    public List<ArtifactRelation> findByArtifactId(String artifactId) {
        return dsl.select()
                .from(table("ARTIFACT_RELATION"))
                .where(field("SOURCE_ARTIFACT_ID").eq(artifactId)
                        .or(field("TARGET_ARTIFACT_ID").eq(artifactId)))
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
                record.get(field("ID"), String.class),
                record.get(field("SOURCE_ARTIFACT_ID"), String.class),
                record.get(field("TARGET_ARTIFACT_ID"), String.class),
                record.get(field("RELATION_TYPE"), String.class));
    }
}

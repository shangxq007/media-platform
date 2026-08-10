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

    public List<ArtifactRelation> findByArtifactId(String artifactId) {
        return dsl.select()
                .from(ARTIFACT_RELATION)
                .where(ARTIFACT_RELATION.SOURCE_ARTIFACT_ID.eq(artifactId)
                        .or(ARTIFACT_RELATION.TARGET_ARTIFACT_ID.eq(artifactId)))
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
                record.get(ARTIFACT_RELATION.ID, String.class),
                record.get(ARTIFACT_RELATION.SOURCE_ARTIFACT_ID, String.class),
                record.get(ARTIFACT_RELATION.TARGET_ARTIFACT_ID, String.class),
                record.get(ARTIFACT_RELATION.RELATION_TYPE, String.class));
    }
}

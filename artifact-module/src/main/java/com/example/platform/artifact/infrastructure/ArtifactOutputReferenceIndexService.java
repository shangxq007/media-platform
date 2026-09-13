package com.example.platform.artifact.infrastructure;
import com.example.platform.artifact.app.*;
import com.example.platform.storage.api.StoragePlacementQuery;
import com.example.platform.shared.identity.ArtifactId;
import java.util.*;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import static com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT;
import static com.example.platform.typedschema.jooq.generated.tables.ArtifactReplica.ARTIFACT_REPLICA;
@Service
public class ArtifactOutputReferenceIndexService implements ArtifactOutputReferenceIndex {
 private final StoragePlacementQuery storage;private final DSLContext dsl;
 public ArtifactOutputReferenceIndexService(StoragePlacementQuery storage,DSLContext dsl){this.storage=storage;this.dsl=dsl;}
 public List<ArtifactOutputReference> findByStorageUri(String uri,String projectId,int limit){
    int cap=Math.min(100,Math.max(1,limit));var results=new LinkedHashSet<ArtifactOutputReference>();
    for(var placement:storage.references(uri,projectId,cap)) {
        var rows=dsl.select(ARTIFACT.ID,ARTIFACT.TENANT_ID,ARTIFACT.PROJECT_ID,ARTIFACT.RENDER_JOB_ID)
            .from(ARTIFACT).join(ARTIFACT_REPLICA).on(ARTIFACT.ID.eq(ARTIFACT_REPLICA.ARTIFACT_ID))
            .where(ARTIFACT.TENANT_ID.eq(placement.owner().tenantId()))
            .and(ARTIFACT.PROJECT_ID.isNotDistinctFrom(placement.owner().projectId()))
            .and(ARTIFACT.PROJECT_ID.isNotNull()).and(ARTIFACT.RENDER_JOB_ID.isNotNull())
            .and(ARTIFACT_REPLICA.STORAGE_OBJECT_ID.eq(placement.objectId().value()))
            .and(ARTIFACT_REPLICA.REPLICA_ID.eq(placement.placement().replicaId().value())).limit(cap-results.size()).fetch();
        for(var row:rows)results.add(new ArtifactOutputReference(new ArtifactScope(row.get(ARTIFACT.TENANT_ID),row.get(ARTIFACT.PROJECT_ID),row.get(ARTIFACT.RENDER_JOB_ID)),new ArtifactId(row.get(ARTIFACT.ID))));
        if(results.size()>=cap)break;
    }return List.copyOf(results);
 }
}

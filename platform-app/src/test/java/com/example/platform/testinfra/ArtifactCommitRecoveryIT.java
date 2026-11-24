package com.example.platform.testinfra;

import com.example.platform.artifact.domain.*;
import com.example.platform.artifact.infrastructure.*;
import com.example.platform.artifact.app.ArtifactRelationRepository;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.test.*;
import com.example.platform.storage.contract.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real canonical V1 receipt/recovery behavior, each scenario owns a fresh schema. */
class ArtifactCommitRecoveryIT {
    private static final Instant NOW=Instant.parse("2026-10-25T00:00:00.123456789Z");
    private static ArtifactCommitRequest request(String artifact,String digest,String provider,Instant time) {
        return new ArtifactCommitRequest(new ArtifactId(artifact),"recovery-tenant",ContentDigest.sha256(digest),3,
                ArtifactMediaType.VIDEO,ArtifactKind.RENDER_MASTER,1,new StorageObjectId("recovery-object"),
                new StorageReplicaId("recovery-replica"),new StorageProviderId(provider),ReplicaRole.PRIMARY,
                "local","recovery-key",List.of(),time,time,null,null);
    }
    private static JooqArtifactCommitService service(org.jooq.DSLContext dsl) {
        return new JooqArtifactCommitService(new ArtifactRepository(dsl),new ArtifactRelationRepository(dsl),dsl);
    }
    private static TestDatabaseSchema database() throws Exception {
        var database=TestDatabaseSchema.open(TestDatabaseConnectionProvider.fromControlledLaunch());
        database.migrateApplication();
        var provider = TestDatabaseConnectionProvider.fromControlledLaunch();
        if (!provider.isIsolatedLocalResource() || !"pgsuite".equals(provider.runId()))
            throw new IllegalStateException("Recovery fixture requires a fresh class-owned PG18 database");
        database.dsl().execute("truncate artifacts.artifact cascade");
        return database;
    }
    @Test void committedReceiptSurvivesRestartAndExactReplayWithoutDuplicateWrites() throws Exception {
        try(var db=database()) {
            var first=service(db.dsl()).commit(request("artifact-recovery","a".repeat(64),"provider-a",NOW));
            var restarted=service(db.dsl());
            assertEquals(first,restarted.findByIdempotencyKey("recovery-tenant","recovery-key").orElseThrow());
            assertTrue(restarted.findByIdempotencyKey("other-tenant","recovery-key").isEmpty());
            assertEquals(first,restarted.commit(request("artifact-recovery","a".repeat(64),"provider-a",NOW.plusSeconds(10))));
            assertEquals(1,db.dsl().fetchCount(org.jooq.impl.DSL.table("artifacts.artifact")));
            assertEquals(1,db.dsl().fetchCount(org.jooq.impl.DSL.table("artifacts.artifact_replica")));
            assertEquals(0,db.dsl().fetchCount(org.jooq.impl.DSL.table("artifacts.artifact_relation")));
        }
    }
    @Test void sameKeyCannotAdoptDifferentIdentityDigestOrProvider() throws Exception {
        try(var db=database()) {
            var owner=service(db.dsl());owner.commit(request("artifact-recovery","a".repeat(64),"provider-a",NOW));
            for(var invalid:List.of(request("different-artifact","a".repeat(64),"provider-a",NOW),
                    request("artifact-recovery","b".repeat(64),"provider-a",NOW),
                    request("artifact-recovery","a".repeat(64),"different-provider",NOW))) {
                var failure=assertThrows(ArtifactErrorCode.ArtifactDomainException.class,()->owner.commit(invalid));
                assertTrue(failure.getMessage().contains("ARTIFACT-409-007"));
                assertEquals(1,db.dsl().fetchCount(org.jooq.impl.DSL.table("artifacts.artifact")));
                assertEquals(1,db.dsl().fetchCount(org.jooq.impl.DSL.table("artifacts.artifact_replica")));
            }
        }
    }
    @Test void unknownOrChangedDurableReceiptFailsClosed() throws Exception {
        try(var db=database()) {
            var owner=service(db.dsl());owner.commit(request("artifact-recovery","a".repeat(64),"provider-a",NOW));
            db.dsl().execute("update artifacts.artifact set audit_provenance='{}'::jsonb where id='artifact-recovery'");
            assertThrows(IllegalStateException.class,()->owner.commit(request("artifact-recovery","a".repeat(64),"provider-a",NOW)));
            assertEquals(1,db.dsl().fetchCount(org.jooq.impl.DSL.table("artifacts.artifact")));
            assertEquals(1,db.dsl().fetchCount(org.jooq.impl.DSL.table("artifacts.artifact_replica")));
        }
    }
    @Test void structuredForgedReceiptEdgeIsRejectedBeforeReplay() throws Exception {
        try(var db=database()) {
            var owner=service(db.dsl());owner.commit(request("artifact-recovery","a".repeat(64),"provider-a",NOW));
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            var forged=new ProvenanceEdge("forged","other-tenant",new ArtifactId("not-committed-parent"),
                    new ArtifactId("artifact-recovery"),ProvenanceRelationType.GENERATED_FROM,"forged-operation",1,
                    "forged-attempt","a".repeat(64),"b".repeat(64),NOW);
            db.dsl().execute("update artifacts.artifact set audit_provenance=jsonb_set(audit_provenance,'{artifactCommitV1,result,provenanceEdges}',cast(? as jsonb)) where id='artifact-recovery'",mapper.writeValueAsString(List.of(forged)));
            assertThrows(IllegalStateException.class,()->owner.findByIdempotencyKey("recovery-tenant","recovery-key"));
            assertThrows(IllegalStateException.class,()->owner.commit(request("artifact-recovery","a".repeat(64),"provider-a",NOW)));
            assertEquals(1,db.dsl().fetchCount(org.jooq.impl.DSL.table("artifacts.artifact")));
            assertEquals(0,db.dsl().fetchCount(org.jooq.impl.DSL.table("artifacts.artifact_relation")));
        }
    }
    @Test void declaredReceiptProvenanceMustStillMatchCanonicalRelations() throws Exception {
        try(var db=database()) {
            var owner=service(db.dsl());
            new ArtifactRepository(db.dsl()).insertRaw(new ArtifactId("recovery-parent"),"recovery-tenant",
                    ContentDigest.sha256("c".repeat(64)),1,ArtifactMediaType.VIDEO,ArtifactKind.SOURCE_MEDIA,ArtifactState.AVAILABLE,null);
            var base=request("artifact-recovery","a".repeat(64),"provider-a",NOW);
            var declaration=new ArtifactCommitRequest.ProvenanceEdgeDeclaration(new ArtifactId("recovery-parent"),
                    ProvenanceRelationType.GENERATED_FROM,"recovery",1,"attempt-a1","a".repeat(64),"b".repeat(64));
            var child=new ArtifactCommitRequest(base.artifactId(),base.tenantId(),base.contentDigest(),base.byteLength(),
                    base.mediaType(),base.artifactKind(),base.schemaVersion(),base.storageObjectId(),base.storageReplicaId(),
                    base.providerId(),base.replicaRole(),base.region(),base.idempotencyKey(),List.of(declaration),NOW,NOW,null,null);
            var accepted=owner.commit(child);
            assertEquals(accepted,service(db.dsl()).findByIdempotencyKey("recovery-tenant","recovery-key").orElseThrow());
            assertEquals(1,db.dsl().execute("delete from artifacts.artifact_relation where source_artifact_id='artifact-recovery'"));
            assertThrows(IllegalStateException.class,()->service(db.dsl()).commit(child));
            assertEquals(2,db.dsl().fetchCount(org.jooq.impl.DSL.table("artifacts.artifact")));
            assertEquals(0,db.dsl().fetchCount(org.jooq.impl.DSL.table("artifacts.artifact_relation")));
        }
    }

    @Test void storedReceiptCreationTimeCannotDriftFromCanonicalArtifactTime() throws Exception {
        try(var db=database()) {
            var owner=service(db.dsl());owner.commit(request("artifact-recovery","a".repeat(64),"provider-a",NOW));
            db.dsl().execute("update artifacts.artifact set audit_provenance=jsonb_set(audit_provenance,'{artifactCommitV1,request,createdAt}',to_jsonb(cast(? as text))) where id='artifact-recovery'",NOW.plusSeconds(86400).toString());
            assertThrows(IllegalStateException.class,()->owner.findByIdempotencyKey("recovery-tenant","recovery-key"));
            assertThrows(IllegalStateException.class,()->owner.commit(request("artifact-recovery","a".repeat(64),"provider-a",NOW)));
            assertEquals(1,db.dsl().fetchCount(org.jooq.impl.DSL.table("artifacts.artifact")));
            assertEquals(1,db.dsl().fetchCount(org.jooq.impl.DSL.table("artifacts.artifact_replica")));
        }
    }

}

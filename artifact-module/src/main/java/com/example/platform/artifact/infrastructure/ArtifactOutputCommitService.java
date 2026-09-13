package com.example.platform.artifact.infrastructure;
import com.example.platform.artifact.app.*;
import com.example.platform.artifact.domain.*;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantGuard;
import com.example.platform.storage.api.*;
import com.example.platform.storage.api.StorageObjectIssuance.IssuanceResult;
import com.example.platform.storage.contract.replica.ReplicaState;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Uses canonical Artifact commit; stable output identity and replay stay under Artifact ownership. */
@Service
public class ArtifactOutputCommitService implements ArtifactOutputCommit {
    private final StoragePlacementQuery storage;
    private final ArtifactCommitService commits;
    private final ArtifactQueryService query;
    private final ArtifactApplicationQuery scopedQuery;
    private final DSLContext dsl;
    private final com.example.platform.outbox.app.OutboxEventService outbox;
    public ArtifactOutputCommitService(StoragePlacementQuery storage, ArtifactCommitService commits,
            ArtifactQueryService query, ArtifactApplicationQuery scopedQuery, DSLContext dsl, com.example.platform.outbox.app.OutboxEventService outbox) {
        this.storage=storage; this.commits=commits; this.query=query; this.scopedQuery=scopedQuery; this.dsl=dsl; this.outbox=outbox;
    }
    @Override @Transactional
    public ArtifactOutputReference commit(ArtifactScope scope, IssuanceResult supplied, ArtifactMediaType mediaType) {
        TenantGuard.assertSameTenant(scope.tenantId());
        Objects.requireNonNull(mediaType);
        var owner=new StorageOwnershipScope(scope.tenantId(),scope.projectId());
        if (!owner.equals(supplied.owner())) throw new IllegalArgumentException("output owner mismatch");
        var receipt=storage.find(owner,supplied.receipt().idempotencyKey()).orElseThrow(()->new IllegalArgumentException("output receipt not persisted"));
        if (!receipt.equals(supplied) || receipt.placement().state()!=ReplicaState.AVAILABLE)
            throw new IllegalArgumentException("output placement mismatch or unavailable");
        var p=receipt.placement();
        if (!scope.tenantId().equals(p.location().namespace().tenantId()) || !scope.projectId().equals(p.location().namespace().projectId()))
            throw new IllegalArgumentException("output namespace mismatch");
        storage.read(owner,receipt.receipt().idempotencyKey()); // current backend integrity, not unchecked URI
        var id=outputId(scope,receipt.objectId());
        dsl.fetch("select pg_advisory_xact_lock(hashtextextended(?, 0))", "artifact-output:"+id.value());
        var existing=query.getArtifact(scope.tenantId(),id);
        if (existing.isPresent()) {
            var artifact=scopedQuery.findArtifact(scope,id).orElseThrow(()->new IllegalArgumentException("output scope mismatch"));
            if (!artifact.contentDigest().matches(p.committedDigest()) || artifact.byteLength()!=p.committedLength()
                    || artifact.mediaType()!=mediaType || artifact.artifactKind()!=ArtifactKind.RENDER_MASTER
                    || artifact.state()!=ArtifactState.AVAILABLE
                    || query.listReplicas(scope.tenantId(),id).stream().noneMatch(b->b.storageObjectId().equals(receipt.objectId())
                       && b.storageReplicaId().equals(p.replicaId()) && b.providerId().equals(p.location().providerId())))
                throw new IllegalArgumentException("output replay differs from accepted Artifact");
        } else {
            var accepted=commits.commit(new ArtifactCommitRequest(id,scope.tenantId(),p.committedDigest(),p.committedLength(),
                mediaType,ArtifactKind.RENDER_MASTER,Artifact.CURRENT_SCHEMA_VERSION,receipt.objectId(),p.replicaId(),
                p.location().providerId(),ReplicaRole.PRIMARY,p.location().region(),"output:"+id.value(),List.of(),
                receipt.receipt().issuedAt(),receipt.receipt().issuedAt(),scope.renderJobId(),scope.projectId()));
            if (!accepted.artifact().artifactId().equals(id)) throw new IllegalStateException("Artifact commit identity mismatch");
            var event=new com.example.platform.artifact.api.event.ArtifactCreatedEvent(new ArtifactOutputReference(scope,id),receipt.receipt().issuedAt());
            outbox.append(com.example.platform.artifact.api.event.ArtifactOutboxEvents.ARTIFACTCREATEDEVENT.append(scope.tenantId(),event,event.factKey()));
        }
        return new ArtifactOutputReference(scope,id);
    }
    static ArtifactId outputId(ArtifactScope scope,com.example.platform.storage.contract.StorageObjectId objectId) {
        return new ArtifactId("art-"+UUID.nameUUIDFromBytes((scope.tenantId()+"\0"+scope.projectId()+"\0"+scope.renderJobId()+"\0"+objectId.value()).getBytes(StandardCharsets.UTF_8)));
    }

}

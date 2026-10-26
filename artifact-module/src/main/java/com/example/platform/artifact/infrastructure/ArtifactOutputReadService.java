package com.example.platform.artifact.infrastructure;
import com.example.platform.artifact.app.*;
import com.example.platform.artifact.domain.*;
import com.example.platform.storage.api.*;
import com.example.platform.shared.web.TenantGuard;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class ArtifactOutputReadService implements ArtifactOutputRead {
 private final ArtifactApplicationQuery scoped;
 private final ArtifactQueryService artifacts;
 private final StoragePlacementQuery storage;
 private final List<AcceptedRenderOutputQuery> accepted;
 private final ArtifactContentRead content;
 public ArtifactOutputReadService(ArtifactApplicationQuery scoped,ArtifactQueryService artifacts,StoragePlacementQuery storage,List<AcceptedRenderOutputQuery> accepted,ArtifactContentRead content){this.scoped=scoped;this.artifacts=artifacts;this.storage=storage;this.accepted=List.copyOf(accepted);this.content=content;}
 public Optional<ArtifactOutputReference> find(ArtifactScope scope) {
    TenantGuard.assertSameTenant(scope.tenantId());
    var found=accepted.stream().map(q->q.find(scope)).flatMap(Optional::stream).distinct().toList();
    if(found.size()>1)throw new IllegalStateException("conflicting accepted Render outputs");
    if(found.size()==1)return Optional.of(new ArtifactOutputReference(scope,found.getFirst()));
    return storage.find(new StorageOwnershipScope(scope.tenantId(),scope.projectId()),new IssuanceIdempotencyKey("render-output:"+scope.renderJobId()))
        .flatMap(receipt->scoped.findArtifact(scope,ArtifactOutputCommitService.outputId(scope,receipt.objectId())))
        .filter(a->a.state()==ArtifactState.AVAILABLE && a.artifactKind()==ArtifactKind.RENDER_MASTER)
        .map(a->new ArtifactOutputReference(scope,a.artifactId()));
 }
 public Content read(ArtifactOutputReference reference) {
    var scope=reference.scope();TenantGuard.assertSameTenant(scope.tenantId());
    var artifact=scoped.findArtifact(scope,reference.artifactId()).orElseThrow(()->new IllegalArgumentException("output not found in scope"));
    if(artifact.state()!=ArtifactState.AVAILABLE || artifact.artifactKind()!=ArtifactKind.RENDER_MASTER)throw new IllegalStateException("output is not available");
    var acceptedIds=accepted.stream().map(q->q.find(scope)).flatMap(Optional::stream).distinct().toList();
    if(acceptedIds.size()>1)throw new IllegalStateException("conflicting accepted Render outputs");
    if(acceptedIds.contains(reference.artifactId())) {
        var verified=content.read(scope,reference.artifactId());
        return new Content(verified.bytes(),artifact.mediaType(),verified.contentType(),verified.fileName());
    }
    var owner=new StorageOwnershipScope(scope.tenantId(),scope.projectId());
    var receipt=storage.find(owner,new IssuanceIdempotencyKey("render-output:"+scope.renderJobId())).orElseThrow(()->new IllegalStateException("output receipt unavailable"));
    if(!artifact.artifactId().equals(ArtifactOutputCommitService.outputId(scope,receipt.objectId())))throw new IllegalArgumentException("not the accepted Render output");
    var replica=artifacts.findReplica(scope.tenantId(),reference.artifactId(),receipt.placement().replicaId()).orElseThrow(()->new IllegalStateException("accepted output replica unavailable"));
    if(!replica.storageObjectId().equals(receipt.objectId()))throw new IllegalStateException("accepted output object mismatch");
    byte[] bytes=storage.read(new StorageOwnershipScope(scope.tenantId(),scope.projectId()),replica.storageObjectId(),replica.storageReplicaId());
    try{
        var digest=com.example.platform.shared.digest.ContentDigest.sha256(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        if(bytes.length!=artifact.byteLength()||!digest.matches(artifact.contentDigest()))throw new IllegalStateException("Artifact content differs from accepted metadata");
    }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    var metadata=storage.reference(owner,replica.storageObjectId(),replica.storageReplicaId());
    var format=ArtifactOutputFormat.require(metadata.mimeType());format.validate(bytes);
    if(format.mediaType()!=artifact.mediaType())throw new IllegalStateException("output MIME differs from accepted Artifact category");
    return new Content(bytes,artifact.mediaType(),format.mimeType(),format.fileName());
 }
}

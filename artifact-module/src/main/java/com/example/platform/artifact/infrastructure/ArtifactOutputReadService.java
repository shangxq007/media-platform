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
 public ArtifactOutputReadService(ArtifactApplicationQuery scoped,ArtifactQueryService artifacts,StoragePlacementQuery storage){this.scoped=scoped;this.artifacts=artifacts;this.storage=storage;}
 public Optional<ArtifactOutputReference> find(ArtifactScope scope) {
    TenantGuard.assertSameTenant(scope.tenantId());
    return storage.find(new StorageOwnershipScope(scope.tenantId(),scope.projectId()),new IssuanceIdempotencyKey("render-output:"+scope.renderJobId()))
        .flatMap(receipt->scoped.findArtifact(scope,ArtifactOutputCommitService.outputId(scope,receipt.objectId())))
        .filter(a->a.state()==ArtifactState.AVAILABLE && a.artifactKind()==ArtifactKind.RENDER_MASTER)
        .map(a->new ArtifactOutputReference(scope,a.artifactId()));
 }
 public Content read(ArtifactOutputReference reference) {
    var scope=reference.scope();TenantGuard.assertSameTenant(scope.tenantId());
    var artifact=scoped.findArtifact(scope,reference.artifactId()).orElseThrow(()->new IllegalArgumentException("output not found in scope"));
    if(artifact.state()!=ArtifactState.AVAILABLE)throw new IllegalStateException("output is not available");
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
    return new Content(bytes,artifact.mediaType());
 }
}

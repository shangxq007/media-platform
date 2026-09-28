package com.example.platform.thumbnail;

import com.example.platform.artifact.domain.*;
import com.example.platform.artifact.app.ArtifactProjectAuthorizationPort;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantGuard;
import com.example.platform.storage.api.*;
import java.util.Arrays;
import org.springframework.stereotype.Service;

@Service
public class ThumbnailArtifactReadService {
    private final ThumbnailTaskStore tasks; private final ArtifactQueryService artifacts; private final StoragePlacementQuery storage; private final ArtifactProjectAuthorizationPort authorization;
    public ThumbnailArtifactReadService(ThumbnailTaskStore tasks,ArtifactQueryService artifacts,StoragePlacementQuery storage,ArtifactProjectAuthorizationPort authorization){this.tasks=tasks;this.artifacts=artifacts;this.storage=storage;this.authorization=authorization;}
    public Image read(String tenant,String project,String taskId){
        TenantGuard.assertSameTenant(tenant); authorization.requireRead(tenant,project); var result=tasks.find(tenant,project,taskId).orElseThrow(()->new IllegalArgumentException("thumbnail not found"));
        if(result.status()!=ThumbnailContracts.Status.COMPLETED||result.artifactId()==null) throw new IllegalStateException("thumbnail is not available");
        Artifact artifact=artifacts.getArtifact(tenant,new ArtifactId(result.artifactId())).orElseThrow(()->new IllegalArgumentException("artifact unavailable"));
        if(artifact.state()!=ArtifactState.AVAILABLE||artifact.artifactKind()!=ArtifactKind.THUMBNAIL||artifact.mediaType()!=ArtifactMediaType.IMAGE) throw new IllegalStateException("artifact is not an accepted thumbnail");
        var binding=artifacts.listReplicas(tenant,artifact.artifactId()).stream().findFirst().orElseThrow(()->new IllegalStateException("thumbnail replica unavailable"));
        var owner=new StorageOwnershipScope(tenant,project);
        byte[] bytes=storage.read(owner,binding.storageObjectId(),binding.storageReplicaId());
        if(bytes.length!=artifact.byteLength()||!digest(bytes).matches(artifact.contentDigest())) throw new IllegalStateException("thumbnail integrity mismatch");
        String type=storage.reference(owner,binding.storageObjectId(),binding.storageReplicaId()).mimeType();
        if(!"image/jpeg".equals(type)&&!"image/png".equals(type)) throw new IllegalStateException("thumbnail MIME is not an accepted image");
        return new Image(bytes,type,artifact.artifactId().value());
    }
    private static com.example.platform.shared.digest.ContentDigest digest(byte[] b){try{return com.example.platform.shared.digest.ContentDigest.sha256(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b)));}catch(Exception e){throw new IllegalStateException(e);}}
    public record Image(byte[] bytes,String contentType,String artifactId){ public Image{bytes=Arrays.copyOf(bytes,bytes.length);} }
}

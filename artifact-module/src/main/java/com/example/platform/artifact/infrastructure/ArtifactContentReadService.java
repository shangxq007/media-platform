package com.example.platform.artifact.infrastructure;

import com.example.platform.artifact.app.*;
import com.example.platform.artifact.domain.*;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.web.TenantGuard;
import com.example.platform.storage.api.VerifiedStorageRead;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ArtifactContentReadService implements ArtifactContentRead {
    private final ArtifactApplicationQuery scoped;
    private final ArtifactQueryService artifacts;
    private final VerifiedStorageRead storage;
    public ArtifactContentReadService(ArtifactApplicationQuery scoped,ArtifactQueryService artifacts,VerifiedStorageRead storage){this.scoped=scoped;this.artifacts=artifacts;this.storage=storage;}
    public Content read(ArtifactScope scope,ArtifactId id) {
        TenantGuard.assertSameTenant(scope.tenantId());
        var artifact=scoped.findArtifact(scope,id).orElseThrow(()->failure(ArtifactApplicationService.ArtifactAccessFailure.NOT_FOUND));
        if(artifact.state()!=ArtifactState.AVAILABLE)throw failure(ArtifactApplicationService.ArtifactAccessFailure.NOT_AVAILABLE);
        if(artifact.byteLength()>64L*1024*1024)throw failure(ArtifactApplicationService.ArtifactAccessFailure.NOT_AVAILABLE);
        var replicas=artifacts.listReplicas(scope.tenantId(),id);
        for(var replica:replicas) {
            try {
                byte[] bytes=storage.read(replica.providerId(),replica.storageObjectId(),artifact.contentDigest(),artifact.byteLength(),64L*1024*1024);
                // Typed artifact category is not MIME authority: recognize a supported container from verified bytes.
                for(var format:ArtifactOutputFormat.values())if(format.mediaType()==artifact.mediaType()) {
                    try{format.validate(bytes);return new Content(bytes,format.mimeType(),format.fileName(),artifact.contentDigest());}
                    catch(IllegalArgumentException unsupportedFormat){}
                }
            }catch(RuntimeException unavailable){ /* bounded alternate replica; no internal failure leaks */ }
        }
        throw failure(ArtifactApplicationService.ArtifactAccessFailure.NOT_AVAILABLE);
    }
    private static ArtifactApplicationService.ArtifactAccessException failure(ArtifactApplicationService.ArtifactAccessFailure type) {
        return new ArtifactApplicationService.ArtifactAccessException(type,type==ArtifactApplicationService.ArtifactAccessFailure.NOT_FOUND?"Artifact not found":"Artifact content unavailable");
    }
}

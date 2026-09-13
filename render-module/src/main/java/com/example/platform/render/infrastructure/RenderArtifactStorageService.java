package com.example.platform.render.infrastructure;

import com.example.platform.artifact.app.*;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.storage.api.*;
import com.example.platform.shared.web.TenantGuard;
import org.springframework.stereotype.Service;

/** Render coordinates published Storage and Artifact commands; neither foreign table is writable here. */
@Service
public class RenderArtifactStorageService {
    private final StorageOutputPort storage;
    private final ArtifactOutputCommit artifacts;
    public RenderArtifactStorageService(StorageOutputPort storage, ArtifactOutputCommit artifacts) {
        this.storage=storage; this.artifacts=artifacts;
    }
    public ArtifactOutputReference uploadJobOutput(String jobId, String projectId,
            String localRelativePath, String contentType) {
        String tenant=TenantGuard.requireTenantId();
        com.example.platform.artifact.domain.ArtifactOutputFormat.require(contentType);
        var written=storage.write(new StorageOutputPort.OutputCommand(new StorageOwnershipScope(tenant,projectId),
                new IssuanceIdempotencyKey("render-output:"+jobId),localRelativePath,contentType));
        return artifacts.commit(new ArtifactScope(tenant,projectId,jobId),written);
    }
}

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
        ArtifactMediaType media=switch(contentType) {
            case "video/mp4", "video/webm", "video/quicktime" -> ArtifactMediaType.VIDEO;
            case "audio/wav", "audio/mpeg", "audio/flac" -> ArtifactMediaType.AUDIO;
            case "image/png", "image/jpeg" -> ArtifactMediaType.IMAGE;
            default -> throw new IllegalArgumentException("unsupported Render output media type");
        };
        var written=storage.write(new StorageOutputPort.OutputCommand(new StorageOwnershipScope(tenant,projectId),
                new IssuanceIdempotencyKey("render-output:"+jobId),localRelativePath,contentType));
        return artifacts.commit(new ArtifactScope(tenant,projectId,jobId),written.issuance(),media);
    }
}

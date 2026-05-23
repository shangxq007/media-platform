package com.example.platform.web.render;

import com.example.platform.artifact.app.ArtifactCatalogService;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.render.api.port.ClientExportArtifactPort;
import org.springframework.stereotype.Component;

@Component
public class ClientExportArtifactAdapter implements ClientExportArtifactPort {

    private final ArtifactCatalogService artifactCatalogService;

    public ClientExportArtifactAdapter(ArtifactCatalogService artifactCatalogService) {
        this.artifactCatalogService = artifactCatalogService;
    }

    @Override
    public RegisteredArtifact register(
            String sessionId,
            String projectId,
            String storageUri,
            String format,
            String resolution,
            long durationSeconds) {
        Artifact artifact = artifactCatalogService.registerArtifact(
                sessionId, projectId, storageUri, format, resolution, durationSeconds);
        String downloadPath = "/api/v1/render/client-exports/" + sessionId + "/download";
        return new RegisteredArtifact(artifact.id(), storageUri, downloadPath);
    }
}

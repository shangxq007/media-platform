package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import java.io.IOException;

/** Backend-neutral Artifact materialization boundary. */
@FunctionalInterface
public interface ArtifactMaterializerPort {
    MaterializedArtifact materialize(String tenantId, ArtifactPin artifactPin) throws IOException;
}

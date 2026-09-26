package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import java.io.IOException;

/** Backend-neutral Artifact materialization boundary. */
@org.springframework.modulith.NamedInterface("runtime")
@FunctionalInterface
public interface ArtifactMaterializerPort {
    ArtifactMaterializationResult materialize(String tenantId, ArtifactPin artifactPin)
            throws IOException;
}

package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.execution.domain.ExecutionInputId;
import java.util.Objects;

/** One typed logical runtime input bound to an immutable worker-local Artifact handle. */
public record MaterializedExecutionInput(
        ExecutionInputId inputId,
        ArtifactPin artifactPin,
        MaterializedArtifact materializedArtifact) {

    public MaterializedExecutionInput {
        Objects.requireNonNull(inputId, "inputId");
        Objects.requireNonNull(artifactPin, "artifactPin");
        Objects.requireNonNull(materializedArtifact, "materializedArtifact");
        if (!artifactPin.equals(materializedArtifact.artifactPin())) {
            throw new IllegalArgumentException(
                    "runtime input Artifact pin must match its materialized local handle");
        }
    }
}

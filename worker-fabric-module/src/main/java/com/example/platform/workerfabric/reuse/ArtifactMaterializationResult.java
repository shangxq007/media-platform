package com.example.platform.workerfabric.reuse;

import java.util.Objects;

/** Materialized local handle plus the bounded disposition observed while acquiring it. */
public record ArtifactMaterializationResult(
        MaterializedArtifact materializedArtifact,
        MaterializationDisposition disposition) {

    public ArtifactMaterializationResult {
        Objects.requireNonNull(materializedArtifact, "materializedArtifact");
        Objects.requireNonNull(disposition, "disposition");
        if (disposition == MaterializationDisposition.FAILURE) {
            throw new IllegalArgumentException(
                    "failed materialization has no successful MaterializedArtifact result");
        }
    }
}

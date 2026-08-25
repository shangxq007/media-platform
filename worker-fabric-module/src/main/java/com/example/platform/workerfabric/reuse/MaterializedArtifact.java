package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Provider-consumable local immutable Artifact materialization. */
public record MaterializedArtifact(ArtifactPin artifactPin, Path path, long byteLength) {
    public MaterializedArtifact {
        Objects.requireNonNull(artifactPin, "artifactPin");
        Objects.requireNonNull(artifactPin.artifactId(), "artifactPin.artifactId");
        Objects.requireNonNull(artifactPin.contentDigest(), "artifactPin.contentDigest");
        Objects.requireNonNull(path, "path");
        path = path.toAbsolutePath().normalize();
        if (byteLength < 0) {
            throw new IllegalArgumentException("byteLength must be non-negative");
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("materialized Artifact path must be a regular file");
        }
    }
}

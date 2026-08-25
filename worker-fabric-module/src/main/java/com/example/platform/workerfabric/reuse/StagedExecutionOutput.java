package com.example.platform.workerfabric.reuse;

import com.example.platform.shared.digest.ContentDigest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Temporary provider output with measured length and digest; not an Artifact or completion. */
public record StagedExecutionOutput(Path path, ContentDigest contentDigest, long byteLength) {
    public StagedExecutionOutput {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(contentDigest, "contentDigest");
        path = path.toAbsolutePath().normalize();
        if (byteLength < 0 || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("staged output must be a measured regular file");
        }
    }
}

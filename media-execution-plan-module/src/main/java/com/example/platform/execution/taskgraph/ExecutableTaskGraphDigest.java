package com.example.platform.execution.taskgraph;

import java.util.Objects;
import java.util.regex.Pattern;

/** Semantic digest of a complete provider-bound executable task graph; never a business ID. */
public record ExecutableTaskGraphDigest(String sha256Hex) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ExecutableTaskGraphDigest {
        Objects.requireNonNull(sha256Hex, "sha256Hex");
        if (!SHA_256.matcher(sha256Hex).matches()) {
            throw new IllegalArgumentException(
                    "ExecutableTaskGraphDigest must be lowercase SHA-256 hex");
        }
    }
}

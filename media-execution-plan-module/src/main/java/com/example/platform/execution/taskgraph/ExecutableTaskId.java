package com.example.platform.execution.taskgraph;

import java.util.Objects;
import java.util.regex.Pattern;

/** Deterministic semantic identity of one immutable provider-bound executable task. */
public record ExecutableTaskId(String sha256Hex) implements Comparable<ExecutableTaskId> {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ExecutableTaskId {
        Objects.requireNonNull(sha256Hex, "sha256Hex");
        if (!SHA_256.matcher(sha256Hex).matches()) {
            throw new IllegalArgumentException("ExecutableTaskId must be lowercase SHA-256 hex");
        }
    }

    @Override
    public int compareTo(ExecutableTaskId other) {
        return sha256Hex.compareTo(other.sha256Hex);
    }
}

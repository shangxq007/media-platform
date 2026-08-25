package com.example.platform.execution.taskgraph;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Versioned deterministic identity of a fully pinned executable computation.
 *
 * <p>This value is derived only from immutable execution semantics. Runtime ownership,
 * placement, attempts, clocks, storage locations and reuse lookup results are deliberately absent.
 */
public record ExecutionReuseKey(
        String version,
        String canonicalSerialization,
        String stableDigest) {

    public static final String VERSION = "execution-reuse-key.v1";
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ExecutionReuseKey {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(canonicalSerialization, "canonicalSerialization");
        Objects.requireNonNull(stableDigest, "stableDigest");
        if (!VERSION.equals(version)) {
            throw new IllegalArgumentException("unsupported ExecutionReuseKey version: " + version);
        }
        if (canonicalSerialization.isBlank()
                || !canonicalSerialization.startsWith("roadmap22.execution-reuse-key.v1")) {
            throw new IllegalArgumentException("invalid ExecutionReuseKey canonical serialization");
        }
        if (!SHA_256.matcher(stableDigest).matches()
                || !ExecutableTaskCanonicalCodec.sha256(canonicalSerialization).equals(stableDigest)) {
            throw new IllegalArgumentException("ExecutionReuseKey digest must match canonical serialization");
        }
    }

    static ExecutionReuseKey fromCanonical(String canonicalSerialization) {
        Objects.requireNonNull(canonicalSerialization, "canonicalSerialization");
        return new ExecutionReuseKey(
                VERSION,
                canonicalSerialization,
                ExecutableTaskCanonicalCodec.sha256(canonicalSerialization));
    }
}

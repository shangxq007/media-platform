package com.example.platform.extension.domain;

import java.util.List;

/** Legacy tool-call projection consumed by the canonical worker-fabric sandbox adapter. */
public record ToolExecutionSafetyPolicy(
        long timeoutMillis,
        String workingDirectory,
        long maxOutputBytes,
        List<String> allowedOutputPaths,
        boolean networkAccess) {

    public ToolExecutionSafetyPolicy {
        if (timeoutMillis <= 0 || maxOutputBytes <= 0) {
            throw new IllegalArgumentException("tool execution safety bounds must be positive");
        }
        allowedOutputPaths = List.copyOf(allowedOutputPaths);
    }

    public static ToolExecutionSafetyPolicy defaults() {
        return new ToolExecutionSafetyPolicy(60_000L, null, 4 * 1024 * 1024, List.of(), false);
    }
}

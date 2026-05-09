package com.example.platform.extension.domain;

import java.util.List;

/**
 * Sandbox policy governing how a tool is executed.
 *
 * <p>All process execution must go through a safe runner. The sandbox policy
 * defines constraints such as timeout, working directory, allowed paths, and
 * output size limits.</p>
 *
 * @param timeoutMillis      maximum execution time in milliseconds (0 = no timeout)
 * @param workingDirectory   working directory for the process (null = inherit)
 * @param maxOutputBytes     maximum stdout/stderr capture size in bytes
 * @param allowedOutputPaths allowed output path prefixes (empty = no restriction)
 * @param networkAccess      whether the tool may access the network
 */
public record ToolSandboxPolicy(
        long timeoutMillis,
        String workingDirectory,
        long maxOutputBytes,
        List<String> allowedOutputPaths,
        boolean networkAccess) {

    /**
     * Default sandbox policy: 60-second timeout, 4MB output cap, no network.
     */
    public static ToolSandboxPolicy defaults() {
        return new ToolSandboxPolicy(60_000L, null, 4 * 1024 * 1024, List.of(), false);
    }
}

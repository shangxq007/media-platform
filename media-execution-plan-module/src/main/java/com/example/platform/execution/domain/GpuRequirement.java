package com.example.platform.execution.domain;

/**
 * GPU requirement classification for an execution step.
 *
 * <p>Closed, version-governed enum — serialized by name for canonical representation.
 */
public enum GpuRequirement {
    /**
     * Step does not use GPU.
     */
    NONE,
    /**
     * Step can use GPU if available, but has CPU fallback.
     */
    OPTIONAL,
    /**
     * Step requires GPU to execute.
     */
    REQUIRED
}

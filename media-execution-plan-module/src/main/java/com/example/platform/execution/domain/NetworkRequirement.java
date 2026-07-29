package com.example.platform.execution.domain;

/**
 * Network requirement classification for an execution step.
 *
 * <p>Closed, version-governed enum — serialized by name for canonical representation.
 */
public enum NetworkRequirement {
    /**
     * No network access required.
     */
    NONE,
    /**
     * Network access preferred but not required (e.g., for model download).
     */
    PREFERRED,
    /**
     * Network access required (e.g., for cloud API calls).
     */
    REQUIRED
}

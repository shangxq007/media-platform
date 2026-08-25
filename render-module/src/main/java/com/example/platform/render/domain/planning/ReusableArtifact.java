package com.example.platform.render.domain.planning;

import java.util.List;
import java.util.Objects;

/**
 * Render-owned reuse candidate metadata. It is advisory only: no Artifact pin, URI, cache lookup,
 * or execution-skip authority is represented here.
 */
public record ReusableArtifact(
        String artifactId,
        String taskId,
        String cacheKey,
        List<Integer> frameRange,
        String scope) {

    public ReusableArtifact {
        Objects.requireNonNull(taskId, "taskId");
        if (taskId.isBlank()) {
            throw new IllegalArgumentException("reuse candidate taskId must not be blank");
        }
        frameRange = frameRange == null ? List.of() : List.copyOf(frameRange);
        cacheKey = cacheKey == null ? "" : cacheKey;
        scope = scope == null ? "" : scope;
    }

    public static ReusableArtifact of(String taskId, String cacheKey) {
        return new ReusableArtifact(taskId, taskId, cacheKey, List.of(), "");
    }
}

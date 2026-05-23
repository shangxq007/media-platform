package com.example.platform.render.domain.timeline.internal;

import java.util.List;

/**
 * Cached render artifact eligible for reuse in an incremental plan.
 */
public record ReusableArtifact(
        String artifactId,
        String taskId,
        String uri,
        String cacheKey,
        List<Integer> frameRange,
        String scope) {

    public static ReusableArtifact of(String taskId, String uri, String cacheKey) {
        return new ReusableArtifact(taskId, taskId, uri, cacheKey, List.of(), "");
    }
}

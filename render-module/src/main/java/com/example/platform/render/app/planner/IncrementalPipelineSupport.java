package com.example.platform.render.app.planner;

import com.example.platform.render.app.MultiProviderPipelineService;
import java.util.Map;

/**
 * Helpers for reporting completed pipeline artifacts. Candidate metadata never skips execution;
 * validated reuse belongs to worker-fabric.
 */
public final class IncrementalPipelineSupport {

    private IncrementalPipelineSupport() {}

    public static Map<String, String> stageArtifactIndex(
            java.util.List<MultiProviderPipelineService.PipelineStageResult> results) {
        java.util.Map<String, String> index = new java.util.LinkedHashMap<>();
        for (MultiProviderPipelineService.PipelineStageResult result : results) {
            if (result.storageUri() != null && !result.storageUri().isBlank()) {
                index.put(result.stageName(), result.storageUri());
            }
        }
        return index;
    }
}

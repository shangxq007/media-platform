package com.example.platform.render.app.planner;

import com.example.platform.render.app.MultiProviderPipelineService;
import java.util.Map;

/**
 * Helpers for applying incremental {@link PipelineExecutionPlan} hints to multi-provider stages.
 */
public final class IncrementalPipelineSupport {

    private IncrementalPipelineSupport() {}

    public static boolean shouldReuse(MultiProviderPipelineService.PipelineStage stage) {
        if (stage.parameters() == null) {
            return false;
        }
        return "true".equalsIgnoreCase(stage.parameters().get("skipExecution"))
                || "reuse".equalsIgnoreCase(stage.parameters().get("incrementalMode"));
    }

    public static String reuseUri(MultiProviderPipelineService.PipelineStage stage) {
        if (stage.parameters() == null) {
            return null;
        }
        String uri = stage.parameters().get("reuseArtifactUri");
        return uri != null && !uri.isBlank() ? uri : null;
    }

    public static String effectiveOutput(MultiProviderPipelineService.PipelineStage stage) {
        String uri = reuseUri(stage);
        if (uri != null) {
            return uri;
        }
        return "/tmp/platform/artifacts/reuse/" + stage.name() + "-output.mp4";
    }

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

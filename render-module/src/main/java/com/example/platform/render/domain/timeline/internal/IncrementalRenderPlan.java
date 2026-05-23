package com.example.platform.render.domain.timeline.internal;

import com.example.platform.render.app.planner.PipelineExecutionPlan;
import java.util.List;
import java.util.Map;

/**
 * Incremental render plan: full DAG plus reuse hints and semantic impact summary.
 */
public record IncrementalRenderPlan(
        String planId,
        String mode,
        int baseRevision,
        int targetRevision,
        boolean fullReRenderRequired,
        SemanticDiffResult diff,
        RenderImpactResult impact,
        PipelineExecutionPlan pipelinePlan,
        List<ReusableArtifact> reuse,
        List<String> executeTaskIds,
        List<String> reuseTaskIds,
        Map<String, String> metadata) {

    public static final String MODE_FULL = "FULL";
    public static final String MODE_INCREMENTAL = "INCREMENTAL";
}

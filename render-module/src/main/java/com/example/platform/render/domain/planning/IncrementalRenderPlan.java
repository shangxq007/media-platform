package com.example.platform.render.domain.planning;

import com.example.platform.timeline.diff.merge.SemanticDiffResult;

import com.example.platform.render.app.planner.PipelineExecutionPlan;
import java.util.List;
import java.util.Map;

/**
 * Incremental render plan: full executable DAG plus advisory reuse candidates and semantic impact.
 * Final skip truth belongs to worker-fabric validation, never this value.
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

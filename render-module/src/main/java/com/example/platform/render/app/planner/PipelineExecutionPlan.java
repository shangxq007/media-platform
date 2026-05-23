package com.example.platform.render.app.planner;

import com.example.platform.render.domain.timeline.FinalComposerHint;
import java.util.List;
import java.util.Map;

/**
 * Server-side render execution plan (DAG) derived from Internal Timeline JSON.
 *
 * <p>Distinct from {@link com.example.platform.render.domain.RenderPlan} which tracks
 * job step execution state.</p>
 */
public record PipelineExecutionPlan(
        String planId,
        String timelineId,
        FinalComposerHint finalComposer,
        List<PipelineTask> tasks,
        Map<String, String> metadata) {

    public List<PipelineTask> tasksOfType(PipelineTaskType type) {
        return tasks.stream().filter(t -> t.type() == type).toList();
    }

    public PipelineTask finalComposeTask() {
        return tasks.stream()
                .filter(t -> t.type() == PipelineTaskType.FINAL_COMPOSE)
                .findFirst()
                .orElse(null);
    }
}

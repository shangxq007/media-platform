package com.example.platform.render.app.planner;

import java.util.List;
import java.util.Map;

/**
 * Single unit of work in a {@link PipelineExecutionPlan} (DAG node).
 */
public record PipelineTask(
        String taskId,
        String name,
        PipelineTaskType type,
        String backend,
        List<String> dependsOn,
        String cacheKey,
        String intermediateFormat,
        Map<String, String> parameters) {

    public static PipelineTask of(String taskId, String name, PipelineTaskType type, String backend,
                                  List<String> dependsOn, Map<String, String> parameters) {
        return new PipelineTask(taskId, name, type, backend, dependsOn, null, null, parameters);
    }
}

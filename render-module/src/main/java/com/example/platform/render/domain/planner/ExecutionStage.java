package com.example.platform.render.domain.planner;

import java.util.List;

/**
 * A stage in the execution plan — steps in a stage run in parallel.
 */
public record ExecutionStage(
        String stageId,
        int order,
        boolean parallel,
        List<ExecutionStep> steps) {

    public static ExecutionStage of(String id, int order, boolean parallel, List<ExecutionStep> steps) {
        return new ExecutionStage(id, order, parallel, steps);
    }
}

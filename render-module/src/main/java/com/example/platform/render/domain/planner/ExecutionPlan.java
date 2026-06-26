package com.example.platform.render.domain.planner;

import java.util.List;

/**
 * Execution plan — the planner's output. Contains ordered stages.
 * No execution logic. Read-only after creation.
 */
public record ExecutionPlan(
        String planId,
        String tenantId,
        String projectId,
        String targetProductId,
        String targetProductType,
        String planStatus,
        List<ExecutionStage> stages,
        String createdAt) {

    public static ExecutionPlan of(String planId, String tenantId, String projectId,
                                     String targetProductId, String targetProductType,
                                     List<ExecutionStage> stages) {
        return new ExecutionPlan(planId, tenantId, projectId, targetProductId,
                targetProductType, "CREATED", stages,
                java.time.Instant.now().toString());
    }
}

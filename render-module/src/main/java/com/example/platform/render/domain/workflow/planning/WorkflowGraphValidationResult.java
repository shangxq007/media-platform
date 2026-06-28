package com.example.platform.render.domain.workflow.planning;

import java.util.List;

/**
 * Validation result for workflow graph structure.
 * Internal domain model.
 */
public record WorkflowGraphValidationResult(
        boolean valid,
        List<WorkflowDryRunIssue> issues) {

    public static WorkflowGraphValidationResult success() {
        return new WorkflowGraphValidationResult(true, List.of());
    }

    public static WorkflowGraphValidationResult failure(List<WorkflowDryRunIssue> issues) {
        return new WorkflowGraphValidationResult(false, issues);
    }
}

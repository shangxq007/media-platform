package com.example.platform.render.domain.workflow.planning;

import com.example.platform.render.domain.workflow.WorkflowStepId;
import com.example.platform.render.domain.workflow.WorkflowStepType;
import java.util.List;
import java.util.Map;

/**
 * Dry-run step summary for a workflow step.
 * Internal domain model. Does not execute.
 */
public record WorkflowDryRunStep(
        WorkflowStepId stepId,
        WorkflowStepType stepType,
        int order,
        WorkflowDryRunStepStatus status,
        WorkflowTemplateStepDryRunSummary templateSummary,
        List<WorkflowDryRunIssue> issues,
        Map<String, String> safeMetadata) {

    public WorkflowDryRunStep {
        if (stepId == null) throw new IllegalArgumentException("Step ID must not be null");
        if (stepType == null) throw new IllegalArgumentException("Step type must not be null");
    }
}

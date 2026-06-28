package com.example.platform.render.domain.workflow.planning;

import com.example.platform.render.domain.workflow.WorkflowDefinitionId;
import com.example.platform.render.domain.workflow.WorkflowVersion;
import java.util.List;
import java.util.Map;

/**
 * Workflow dry-run plan — validates and orders workflow steps without executing.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public record WorkflowDryRunPlan(
        WorkflowDryRunPlanId id,
        WorkflowDefinitionId workflowDefinitionId,
        WorkflowVersion workflowVersion,
        List<WorkflowDryRunStep> steps,
        List<WorkflowDryRunIssue> issues,
        boolean valid,
        Map<String, String> safeMetadata) {

    public WorkflowDryRunPlan {
        if (id == null) throw new IllegalArgumentException("Plan ID must not be null");
    }

    public boolean hasBlockingIssues() {
        return issues.stream().anyMatch(WorkflowDryRunIssue::isBlocking);
    }

    public long readyStepCount() {
        return steps.stream()
                .filter(s -> s.status() == WorkflowDryRunStepStatus.READY)
                .count();
    }
}

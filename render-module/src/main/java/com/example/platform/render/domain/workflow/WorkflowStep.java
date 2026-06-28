package com.example.platform.render.domain.workflow;

import java.util.List;
import java.util.Map;

/**
 * Ordered or dependency-based unit of workflow intent.
 * Internal domain model. Provider-neutral, storage-neutral.
 */
public record WorkflowStep(
        WorkflowStepId id,
        WorkflowStepType type,
        List<WorkflowStepDependency> dependencies,
        Map<String, WorkflowParameterValue> parameters,
        WorkflowTemplateApplicationStepSpec templateApplicationSpec,
        Map<String, String> safeMetadata) {

    public WorkflowStep {
        if (id == null) throw new IllegalArgumentException("Step ID must not be null");
        if (type == null) throw new IllegalArgumentException("Step type must not be null");
    }

    public boolean isApplyTemplate() {
        return type == WorkflowStepType.APPLY_TEMPLATE;
    }
}

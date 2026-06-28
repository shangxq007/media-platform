package com.example.platform.render.domain.workflow.planning;

public record WorkflowDryRunPlanId(String value) {
    public WorkflowDryRunPlanId {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("WorkflowDryRunPlanId must not be blank");
    }
}

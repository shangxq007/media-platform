package com.example.platform.render.domain.timeline.diff.calculation;

import java.util.Map;

/**
 * Workflow step snapshot for diff input. Internal domain model.
 */
public record CanonicalTimelineWorkflowStepSnapshot(
        String workflowStepId,
        String stepType,
        String templateApplicationId,
        Map<String, String> safeMetadata) {

    public CanonicalTimelineWorkflowStepSnapshot {
        if (workflowStepId == null || workflowStepId.isBlank())
            throw new IllegalArgumentException("workflowStepId must not be blank");
    }
}

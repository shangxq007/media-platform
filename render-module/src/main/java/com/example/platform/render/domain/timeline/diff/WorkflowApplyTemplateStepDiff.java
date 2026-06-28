package com.example.platform.render.domain.timeline.diff;

import java.util.List;
import java.util.Map;

/**
 * Diff bridge for workflow APPLY_TEMPLATE step changes.
 * Internal domain model. Does not execute workflows.
 */
public record WorkflowApplyTemplateStepDiff(
        String workflowStepId,
        String templateApplicationId,
        List<TimelineChangeOperation> operations,
        Map<String, String> safeMetadata) {}

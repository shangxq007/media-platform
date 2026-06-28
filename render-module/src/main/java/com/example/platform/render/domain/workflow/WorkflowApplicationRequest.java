package com.example.platform.render.domain.workflow;

import java.util.List;
import java.util.Map;

/**
 * Request to apply/invoke a workflow definition.
 * Internal domain model.
 */
public record WorkflowApplicationRequest(
        String projectId,
        WorkflowDefinitionId workflowId,
        WorkflowVersion workflowVersion,
        List<WorkflowParameter> parameters,
        Map<String, String> safeMetadata) {

    public WorkflowApplicationRequest {
        if (projectId == null || projectId.isBlank())
            throw new IllegalArgumentException("Project ID must not be blank");
        if (workflowId == null)
            throw new IllegalArgumentException("Workflow ID must not be null");
    }
}

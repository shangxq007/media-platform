package com.example.platform.workflow.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface RenderWorkflow {

    @WorkflowMethod
    String run(String renderJobId, String tenantId);
}

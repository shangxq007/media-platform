package com.example.platform.workflow.temporal.mediatask;

import io.temporal.workflow.*;
@WorkflowInterface
public interface CapabilityExecutionWorkflow {
    @WorkflowMethod String run(String tenantId,String projectId,String executionId,long generation);
}

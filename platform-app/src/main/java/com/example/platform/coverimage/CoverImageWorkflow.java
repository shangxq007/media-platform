package com.example.platform.coverimage;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface CoverImageWorkflow {

    @WorkflowMethod
    String run(String taskId, String tenantId, String projectId);

    @SignalMethod
    void cancel(String reason);

    @QueryMethod
    String status();
}

package com.example.platform.thumbnail;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ThumbnailWorkflow {
    @WorkflowMethod String run(String taskId, String tenantId, String projectId);
    @SignalMethod void cancel(String reason);
    @QueryMethod String status();
}

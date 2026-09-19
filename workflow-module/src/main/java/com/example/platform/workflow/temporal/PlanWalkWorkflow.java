package com.example.platform.workflow.temporal;

import io.temporal.workflow.*;
import java.util.Map;

@WorkflowInterface
public interface PlanWalkWorkflow {
    @WorkflowMethod Map<String,String> execute(String runId, String planJson, Map<String,String> inputs);
    @SignalMethod void release(String stepId, String releaseId, boolean approved);
    @SignalMethod void cancelRun();
}

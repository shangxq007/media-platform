package com.example.platform.workflow.temporal;

import io.temporal.activity.*;
import java.util.Map;

/** Application boundaries must deduplicate effect and projection commands by logical step identity. */
@ActivityInterface
public interface PlanWalkActivities {
    @ActivityMethod String invoke(String runId, String stepId, String planJson, String nodeId, Map<String,String> bindings);
    @ActivityMethod void waiting(String runId, String stepId, String kind, long deadlineMillis);
    @ActivityMethod void stepCompleted(String runId, String stepId, String resultJson);
    @ActivityMethod String terminal(String runId, String status, String failureCode,String failedStep);
}

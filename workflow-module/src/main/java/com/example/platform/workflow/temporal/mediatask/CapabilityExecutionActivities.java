package com.example.platform.workflow.temporal.mediatask;

import io.temporal.activity.ActivityInterface;

/** Generic Artifact-sourced execution adapter; lifecycle and publication remain Execution-owned. */
@ActivityInterface
public interface CapabilityExecutionActivities {
    void executeCapability(String tenantId,String projectId,String executionId,long generation);
    void failCapability(String tenantId,String projectId,String executionId,long generation,boolean cancelled,String failureCode);
}

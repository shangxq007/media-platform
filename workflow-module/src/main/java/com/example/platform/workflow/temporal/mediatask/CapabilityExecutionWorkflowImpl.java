package com.example.platform.workflow.temporal.mediatask;

import com.example.platform.workflow.temporal.RenderTaskQueue;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.*;
import java.time.Duration;

/** Durable dispatch/retry only; no purpose-specific task state or Artifact completion authority. */
@WorkflowImpl(taskQueues=RenderTaskQueue.NAME)
public class CapabilityExecutionWorkflowImpl implements CapabilityExecutionWorkflow {
    private final CapabilityExecutionActivities activities=Workflow.newActivityStub(CapabilityExecutionActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(2)).setHeartbeatTimeout(Duration.ofSeconds(15))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).setInitialInterval(Duration.ofSeconds(1))
                            .setDoNotRetry(IllegalArgumentException.class.getName(),java.util.concurrent.CancellationException.class.getName()).build()).build());
    private final CapabilityExecutionActivities terminal=Workflow.newActivityStub(CapabilityExecutionActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(1))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumInterval(Duration.ofSeconds(30)).build()).build());
    @Override public String run(String tenant,String project,String execution,long generation) {
        try {
            activities.executeCapability(tenant,project,execution,generation);
            return execution;
        } catch(io.temporal.failure.TemporalFailure failure) {
            boolean cancelled=CancellationScope.current().isCancelRequested();
            Workflow.newDetachedCancellationScope(()->terminal.failCapability(tenant,project,execution,generation,
                    cancelled,"CAPABILITY_EXECUTION_FAILED")).run();
            throw failure;
        }
    }
}

package com.example.platform.workflow.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * Workflow code stays deterministic; flag evaluation runs inside {@link RenderActivities}.
 */
@WorkflowImpl(taskQueues = RenderTaskQueue.NAME)
public class RenderWorkflowImpl implements RenderWorkflow {

    private final RenderActivities activities = Workflow.newActivityStub(
            RenderActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .build());

    @Override
    public String run(String renderJobId, String tenantId) {
        String pipeline = activities.decideRenderPipeline(renderJobId, tenantId);
        return "render-" + pipeline + ":" + renderJobId;
    }
}

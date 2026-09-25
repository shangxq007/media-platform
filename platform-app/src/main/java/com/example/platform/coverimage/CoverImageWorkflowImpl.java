package com.example.platform.coverimage;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.CanceledFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/** Cover-image workflow; one activity, one queue, no provider mechanics. */
@WorkflowImpl(taskQueues = "media-platform-tasks")
public class CoverImageWorkflowImpl implements CoverImageWorkflow {

    private final CoverImageActivities activities = Workflow.newActivityStub(
            CoverImageActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .setHeartbeatTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2)
                            .setDoNotRetry(IllegalArgumentException.class.getName())
                            .build())
                    .build());

    private String state = "RUNNING";
    private boolean cancelled;

    @Override
    public String run(String taskId, String tenantId, String projectId) {
        Workflow.getVersion("COVER-IMAGE-WF-V1", Workflow.DEFAULT_VERSION, 1);
        if (cancelled) {
            state = "CANCELLED";
            throw new CanceledFailure("cover-image cancelled");
        }
        try {
            String artifactId = activities.renderAndCommit(taskId, tenantId, projectId);
            if (cancelled || artifactId == null) {
                state = "CANCELLED";
                throw new CanceledFailure("cover-image cancelled");
            }
            state = "COMPLETED";
            return artifactId;
        } catch (CanceledFailure cancelledFailure) {
            state = "CANCELLED";
            throw cancelledFailure;
        } catch (RuntimeException failure) {
            state = "FAILED";
            throw failure;
        }
    }

    @Override
    public void cancel(String reason) {
        cancelled = true;
        state = "CANCELLED";
    }

    @Override
    public String status() {
        return state;
    }
}

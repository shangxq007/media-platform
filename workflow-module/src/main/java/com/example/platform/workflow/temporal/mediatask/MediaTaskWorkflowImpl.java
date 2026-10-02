package com.example.platform.workflow.temporal.mediatask;

import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.workflow.temporal.RenderTaskQueue;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.List;

/**
 * Skeleton implementation of {@link MediaTaskWorkflow} (P2-5b-1-R2).
 *
 * <p>Deterministic workflow code only: no clock, no random, no I/O, no repository access — every
 * side effect happens inside {@link MediaTaskActivities}. The workflow iterates nothing yet: the
 * whole graph is one activity (Option C, decided). {@code executeTask} is declared but not called
 * until P2-5b-2, so this skeleton stops after {@code prepareTask} and reports PREPARED.
 *
 * <p>Retry policy is bounded and mirrors {@code RenderWorkflowImpl}: a retry re-invokes the same
 * activity with the same stable reference; it must never mint a new attempt/generation (the
 * platform attempt identity is created outside Temporal and resolved by the activity).
 */
@WorkflowImpl(taskQueues = RenderTaskQueue.NAME)
public class MediaTaskWorkflowImpl implements MediaTaskWorkflow {

    private static final String VERSION_MARKER = "P25B1-MEDIA-TASK-WF-V1";

    private final MediaTaskActivities activities = Workflow.newActivityStub(
            MediaTaskActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofHours(2))
                    .setHeartbeatTimeout(Duration.ofMinutes(5))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2.0)
                            .setDoNotRetry(IllegalArgumentException.class.getName())
                            .build())
                    .build());

    @Override
    public MediaTaskWorkflowResult run(BoundGraphReference reference, String tenantId) {
        Workflow.getVersion(VERSION_MARKER, Workflow.DEFAULT_VERSION, 1);
        if (reference == null) {
            throw new IllegalArgumentException("bound graph reference is required");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (!tenantId.equals(reference.tenantId())) {
            throw new IllegalArgumentException("tenantId must match the bound graph reference scope");
        }

        PreparedTaskRef prepared = activities.prepareTask(reference, tenantId);

        // P2-5b-2: activities.executeTask(prepared, tenantId) — whole-graph execution (Option C).
        // The call is intentionally absent so no partially-wired execution can run.
        List<String> taskIds = prepared.executableTaskIds();
        return new MediaTaskWorkflowResult(
                prepared.tenantId(),
                prepared.renderJobId(),
                prepared.planDigest(),
                prepared.expectedExecutableTaskGraphDigest(),
                taskIds,
                "PREPARED");
    }
}

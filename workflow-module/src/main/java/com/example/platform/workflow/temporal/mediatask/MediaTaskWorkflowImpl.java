package com.example.platform.workflow.temporal.mediatask;

import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.workflow.temporal.RenderTaskQueue;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/** Deterministic workflow: prepare and execute the verified graph, then await durable Render terminal projection. Every domain side effect is an activity; projection retries do not repeat FFmpeg. */
@WorkflowImpl(taskQueues = RenderTaskQueue.NAME)
public class MediaTaskWorkflowImpl implements MediaTaskWorkflow {

    private static final String VERSION_MARKER = "P25B1-MEDIA-TASK-WF-V1";

    /**
     * Deterministic precondition failures the activity must not retry: a retry can only reproduce
     * the same outcome. Referenced by fully-qualified name because this module does not depend on the
     * modules that own the exceptions. Transient failures (database faults, provider execution
     * faults, I/O) deliberately stay retryable — see {@link #mediaTaskActivityRetryOptions()}.
     *
     * <ul>
     *   <li>{@code IllegalArgumentException} — invalid workflow arguments (tenant mismatch) and an
     *       unconfigured publication scope (fail closed, never an invented value).</li>
     *   <li>{@code TaskRuntimeExecutionConstructionException} — {@code GRANT_ABSENT},
     *       {@code GRANT_MINT_REJECTED}, {@code PROVIDER_CONTRIBUTION_ABSENT/UNAVAILABLE}, missing
     *       durable output target or artifact commit metadata.</li>
     *   <li>{@code TaskOutputPublicationPlanningException} — the task output publication intent
     *       cannot be planned.</li>
     *   <li>{@code BoundGraphDigestMismatchException} — the durable graph and the current binding
     *       inputs disagree; re-running the same reference cannot repair that.</li>
     * </ul>
     */
    private static final String[] DETERMINISTIC_NO_RETRY_TYPES = {
        IllegalArgumentException.class.getName(),
        "com.example.platform.workerfabric.reuse.TaskRuntimeExecutionConstructionException",
        "com.example.platform.workerfabric.reuse.TaskOutputPublicationPlanningException",
        "com.example.platform.execution.binding.BoundGraphDigestMismatchException",
    };

    /**
     * Bounded retry policy for the media-task activities. Capacity-only retryable admission
     * failures use the full bound; all deterministic no-retry types remain non-retryable.
     */
    static RetryOptions mediaTaskActivityRetryOptions() {
        return RetryOptions.newBuilder()
                .setMaximumAttempts(20)
                .setInitialInterval(Duration.ofSeconds(2))
                .setMaximumInterval(Duration.ofSeconds(15))
                .setBackoffCoefficient(2.0)
                .setDoNotRetry(DETERMINISTIC_NO_RETRY_TYPES)
                .build();
    }

    private final MediaTaskActivities activities = Workflow.newActivityStub(
            MediaTaskActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofHours(2))
                    .setHeartbeatTimeout(Duration.ofMinutes(5))
                    .setRetryOptions(mediaTaskActivityRetryOptions())
                    .build());

    // Terminal projection is a durable Temporal retry, not a successful Workflow with a lost DB write.
    private final MediaTaskActivities terminalActivities=Workflow.newActivityStub(MediaTaskActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofMinutes(2))
            .setRetryOptions(RetryOptions.newBuilder().setInitialInterval(Duration.ofSeconds(1))
                    .setMaximumInterval(Duration.ofSeconds(30)).setDoNotRetry(DETERMINISTIC_NO_RETRY_TYPES).build()).build());

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

        try {
            PreparedTaskRef prepared = activities.prepareTask(reference, tenantId);
            MediaTaskWorkflowResult result=activities.executeTask(prepared, tenantId);
            // Separate retriable projection: a database/outbox fault must not rerun FFmpeg.
            terminalActivities.completeTask(prepared,result,tenantId);
            return result;
        } catch (io.temporal.failure.TemporalFailure failure) {
            boolean cancelled=io.temporal.workflow.CancellationScope.current().isCancelRequested();
            Workflow.newDetachedCancellationScope(()->terminalActivities.failTask(reference,tenantId,cancelled)).run();
            throw failure;
        }
    }
}

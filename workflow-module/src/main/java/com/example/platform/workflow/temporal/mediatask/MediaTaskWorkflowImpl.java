package com.example.platform.workflow.temporal.mediatask;

import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.workflow.temporal.RenderTaskQueue;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;
import java.time.Duration;

/**
 * Skeleton implementation of {@link MediaTaskWorkflow} (P2-5b-1-R2).
 *
 * <p>Deterministic workflow code only: no clock, no random, no I/O, no repository access — every
 * side effect happens inside {@link MediaTaskActivities}. The workflow iterates nothing yet: the
 * whole graph is one activity (Option C, decided). The workflow prepares the graph and then executes
 * it in one further activity call (P2-5b-2b-2a); the activity owns every side effect.
 *
 * <p>Retry policy is bounded and mirrors {@code RenderWorkflowImpl}: a retry re-invokes the same
 * activity with the same stable reference; it must never mint a new attempt/generation (the
 * platform attempt identity is created outside Temporal and resolved by the activity).
 */
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
     * Bounded retry policy for the media-task activities, unchanged except for the deterministic
     * no-retry classification (attempts 3, initial 1 s, coefficient 2.0).
     */
    static RetryOptions mediaTaskActivityRetryOptions() {
        return RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .setInitialInterval(Duration.ofSeconds(1))
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

        // Whole-graph execution (Option C): one activity call executes every task of the graph.
        return activities.executeTask(prepared, tenantId);
    }
}

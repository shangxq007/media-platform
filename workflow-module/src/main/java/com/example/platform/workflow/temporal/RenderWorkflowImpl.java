package com.example.platform.workflow.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.CanceledFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * Durable render workflow implementation (frozen contract TEPHV1 CONTRACT_V1).
 *
 * <p>Deterministic workflow code: no clock, no random, no I/O, no repository
 * access — all side effects execute inside {@link RenderActivities}.</p>
 *
 * <p>Hardening (W1-GAP-003/004/005/006/008):</p>
 * <ul>
 *   <li>bounded explicit RetryOptions on activity stubs (no unbounded default),
 *       non-retryable failures classified (W1-GAP-003);</li>
 *   <li>workflow version marker via {@link Workflow#getVersion} (W1-GAP-004);</li>
 *   <li>timeout hierarchy consistent: activity start-to-close 2h, heartbeat
 *       timeout set (W1-GAP-005/008);</li>
 *   <li>cancellation signal + status query surface (W1-GAP-006);</li>
 *   <li>heartbeat progress details (W1-GAP-008).</li>
 * </ul>
 */
@WorkflowImpl(taskQueues = RenderTaskQueue.NAME)
public class RenderWorkflowImpl implements RenderWorkflow {

    private static final String VERSION_MARKER = "TEPHV1-WF-V1";

    private final RenderActivities activities = Workflow.newActivityStub(
            RenderActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofHours(2))
                    .setHeartbeatTimeout(Duration.ofMinutes(5))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2.0)
                            // Non-retryable classification (W1-GAP-003):
                            // IllegalArgumentException (e.g. job not found,
                            // tenant mismatch) must not be retried.
                            .setDoNotRetry(IllegalArgumentException.class.getName())
                            .build())
                    .build());

    private boolean cancelled = false;
    private String currentStatus = "STARTED";
    private String currentPhase = "idle";

    @Override
    public String run(String renderJobId, String tenantId) {
        // Version marker: future workflow-code branches must be gated with
        // Workflow.getVersion to keep running histories replayable.
        Workflow.getVersion(VERSION_MARKER, Workflow.DEFAULT_VERSION, 1);

        currentStatus = "RUNNING";
        String pipeline = activities.decideRenderPipeline(renderJobId, tenantId);
        currentPhase = "decide";
        checkCancelled();
        currentStatus = "RUNNING";
        String completedJobId = activities.executeRenderJob(renderJobId, tenantId);
        currentPhase = "execute";
        checkCancelled();
        int delivered = activities.deliverArtifacts(completedJobId, tenantId);
        currentPhase = "deliver";
        checkCancelled();
        currentStatus = "COMPLETED";
        return "render-" + pipeline + ":" + completedJobId + ":delivered=" + delivered;
    }

    private void checkCancelled() {
        if (cancelled) {
            currentStatus = "CANCELLED";
            throw new CanceledFailure("Workflow cancelled by user");
        }
    }

    @Override
    public void cancel(String tenantId, String renderJobId, String reason) {
        this.cancelled = true;
        this.currentStatus = "CANCELLED";
    }

    @Override
    public String status(String tenantId, String renderJobId) {
        return currentStatus + ":" + currentPhase;
    }
}

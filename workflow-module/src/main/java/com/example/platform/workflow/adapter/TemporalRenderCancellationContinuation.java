package com.example.platform.workflow.adapter;

import com.example.platform.render.api.port.RenderJobCancellationContinuation;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Temporal implementation of {@link RenderJobCancellationContinuation}
 * (frozen contract TEPHV1 CONTRACT_V1, W1-GAP-006).
 *
 * <p>Active when {@code render.execution.mode=temporal}: cancels the durable
 * workflow whose workflowId equals the render job id. Cancellation reaches
 * the workflow (cancel signal handler marks CANCELLED) and is distinguishable
 * from failure via the workflow status query. Does NOT mutate Timeline,
 * Product READY, RenderExecutionPlan or Plugin state.</p>
 */
@Component
@ConditionalOnProperty(prefix = "render.execution", name = "mode", havingValue = "temporal")
public class TemporalRenderCancellationContinuation implements RenderJobCancellationContinuation {

    private static final Logger log = LoggerFactory.getLogger(TemporalRenderCancellationContinuation.class);

    private final WorkflowClient workflowClient;

    public TemporalRenderCancellationContinuation(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public void cancelAfterJobCancelled(String tenantId, String jobId) {
        log.info("Cancelling Temporal render workflow: jobId={} tenant={}", jobId, tenantId);
        try {
            WorkflowStub stub = workflowClient.newUntypedWorkflowStub(jobId);
            stub.cancel();
        } catch (Exception e) {
            log.warn("Temporal workflow cancellation failed or already terminated: jobId={}",
                    jobId, e);
        }
    }
}

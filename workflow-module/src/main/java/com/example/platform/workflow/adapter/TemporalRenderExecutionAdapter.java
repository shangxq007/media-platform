package com.example.platform.workflow.adapter;

import com.example.platform.workflow.port.RenderExecutionPort;
import com.example.platform.workflow.temporal.RenderTaskQueue;
import com.example.platform.workflow.temporal.RenderWorkflow;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Temporal implementation of {@link RenderExecutionPort} that starts
 * Temporal workflows for distributed, durable execution.
 *
 * <p>This adapter is activated when {@code render.execution.mode=temporal}.
 * It requires a running Temporal Server and is suitable for:</p>
 * <ul>
 *   <li>Production deployments requiring durability and fault tolerance</li>
 *   <li>Distributed systems with multiple workers</li>
 *   <li>Long-running render jobs that benefit from Temporal's retry and timeout capabilities</li>
 * </ul>
 *
 * <h3>Hardening (frozen contract TEPHV1 CONTRACT_V1)</h3>
 * <ul>
 *   <li>W1-GAP-005 timeout consistency: workflow run timeout raised to 2h30m
 *       (run &gt;= max activity start-to-close 2h) and execution timeout 3h
 *       (execution &gt;= run); no conflicting hierarchy.</li>
 *   <li>W1-GAP-007 explicit WorkflowIdReusePolicy.ALLOW_DUPLICATE_FAILED_ONLY:
 *       a completed workflowId may not be reused for a new run; the render
 *       job id stays the business id (never replaced by a Temporal id).</li>
 *   <li>W1-GAP-011 memo: tenantId / projectId / jobId set at start (no PII,
 *       no secrets) for workflow-level correlation.</li>
 *   <li>W1-GAP-012 observability: runId is captured from the started
 *       WorkflowExecution and logged for correlation.</li>
 * </ul>
 *
 * @see RenderExecutionPort
 * @see LocalRenderExecutionAdapter
 * @see com.example.platform.workflow.temporal.RenderWorkflow
 * @see com.example.platform.workflow.temporal.RenderWorkflowImpl
 */
@Component
@ConditionalOnProperty(prefix = "render.execution", name = "mode", havingValue = "temporal")
public class TemporalRenderExecutionAdapter implements RenderExecutionPort {

    private static final Logger log = LoggerFactory.getLogger(TemporalRenderExecutionAdapter.class);

    private final WorkflowClient workflowClient;

    /**
     * Creates a new TemporalRenderExecutionAdapter.
     *
     * @param workflowClient the Temporal workflow client (must not be null)
     */
    public TemporalRenderExecutionAdapter(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    /**
     * Execute a render job by starting a Temporal workflow.
     *
     * @param renderJobId the unique identifier for the render job (used as Temporal workflow ID)
     * @param tenantId    the tenant identifier
     * @param projectId   the project identifier
     * @param prompt      the AI prompt/script
     * @param profile     the render profile
     * @return the render job ID (same as workflow ID)
     * @throws IllegalStateException if Temporal Server is unavailable or workflow start fails
     */
    @Override
    public String execute(String renderJobId, String tenantId, String projectId,
                          String prompt, String profile) {
        log.info("Starting Temporal render workflow: jobId={}, tenant={}, project={}",
                renderJobId, tenantId, projectId);

        try {
            Map<String, Object> memo = new HashMap<>();
            memo.put("tenantId", tenantId != null ? tenantId : "");
            memo.put("projectId", projectId != null ? projectId : "");
            memo.put("jobId", renderJobId);

            RenderWorkflow workflow = workflowClient.newWorkflowStub(
                    RenderWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setWorkflowId(renderJobId)
                            .setTaskQueue(RenderTaskQueue.NAME)
                            // W1-GAP-005: execution >= run >= max activity S2C (2h)
                            .setWorkflowExecutionTimeout(Duration.ofHours(3))
                            .setWorkflowRunTimeout(Duration.ofMinutes(150))
                            // W1-GAP-007: completed workflow ids are not reused
                            .setWorkflowIdReusePolicy(
                                    WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY)
                            // W1-GAP-011: correlation memo (no PII, no secrets)
                            .setMemo(memo)
                            .build());

            // Start workflow asynchronously via untyped stub;
            // Temporal handles durability, retries, and worker dispatch.
            WorkflowStub stub = WorkflowStub.fromTyped(workflow);
            WorkflowExecution execution = stub.start(renderJobId, tenantId);

            // W1-GAP-012: runId correlation
            String runId = execution.getRunId();
            log.info("Temporal workflow started successfully: workflowId={}, runId={}, taskQueue={}",
                    renderJobId, runId, RenderTaskQueue.NAME);
            return renderJobId;

        } catch (Exception e) {
            log.error("Failed to start Temporal workflow: jobId={}", renderJobId, e);
            throw new IllegalStateException(
                    "Failed to start Temporal render workflow for job: " + renderJobId, e);
        }
    }
}

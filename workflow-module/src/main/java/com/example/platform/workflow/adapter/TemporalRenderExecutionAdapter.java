package com.example.platform.workflow.adapter;

import com.example.platform.workflow.port.RenderExecutionPort;
import com.example.platform.workflow.temporal.RenderTaskQueue;
import com.example.platform.workflow.temporal.RenderWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

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
 * <h3>Execution Flow</h3>
 * <ol>
 *   <li>Receive execution request</li>
 *   <li>Create workflow options with appropriate timeouts and retry policy</li>
 *   <li>Start Temporal workflow via {@link WorkflowClient}</li>
 *   <li>Return the workflow ID (render job ID)</li>
 * </ol>
 *
 * <h3>Configuration</h3>
 * <pre>{@code
 * render:
 *   execution:
 *     mode: temporal
 * spring:
 *   temporal:
 *     connection:
 *       target: 127.0.0.1:7233
 *     namespace: media-platform
 *     start-workers: true
 * }</pre>
 *
 * <h3>Graceful Degradation</h3>
 * <p>If Temporal Server is unavailable, this adapter will log an error and
 * throw {@link IllegalStateException}. Ensure proper health checks and
 * circuit breakers are in place.</p>
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
     * @param projectId   the project identifier (logged for audit; full details in workflow memo)
     * @param prompt      the AI prompt/script (logged for audit; full details in workflow memo)
     * @param profile     the render profile (logged for audit; full details in workflow memo)
     * @return the render job ID (same as workflow ID)
     * @throws IllegalStateException if Temporal Server is unavailable or workflow start fails
     */
    @Override
    public String execute(String renderJobId, String tenantId, String projectId,
                          String prompt, String profile) {
        log.info("Starting Temporal render workflow: jobId={}, tenant={}, project={}",
                renderJobId, tenantId, projectId);

        try {
            RenderWorkflow workflow = workflowClient.newWorkflowStub(
                    RenderWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setWorkflowId(renderJobId)
                            .setTaskQueue(RenderTaskQueue.NAME)
                            .setWorkflowExecutionTimeout(Duration.ofHours(2))
                            .setWorkflowRunTimeout(Duration.ofMinutes(30))
                            .build());

            // Start workflow asynchronously via untyped stub;
            // Temporal handles durability, retries, and worker dispatch.
            WorkflowStub stub = WorkflowStub.fromTyped(workflow);
            stub.start(renderJobId, tenantId);

            log.info("Temporal workflow started successfully: workflowId={}, taskQueue={}",
                    renderJobId, RenderTaskQueue.NAME);
            return renderJobId;

        } catch (Exception e) {
            log.error("Failed to start Temporal workflow: jobId={}", renderJobId, e);
            throw new IllegalStateException(
                    "Failed to start Temporal render workflow for job: " + renderJobId, e);
        }
    }
}

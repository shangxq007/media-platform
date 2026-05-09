package com.example.platform.workflow.adapter;

import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.api.port.RenderOrchestratorPort;
import com.example.platform.workflow.port.RenderExecutionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local implementation of {@link RenderExecutionPort} that delegates to
 * {@link RenderOrchestratorPort} for direct, synchronous execution.
 *
 * <p>This adapter is activated when {@code render.execution.mode=local} (default).
 * It does not require a Temporal Server and is suitable for:</p>
 * <ul>
 *   <li>Local development</li>
 *   <li>Integration testing</li>
 *   <li>Simple deployments without distributed orchestration needs</li>
 * </ul>
 *
 * <h3>Execution Flow</h3>
 * <ol>
 *   <li>Receive execution request</li>
 *   <li>Delegate to {@code RenderOrchestratorPort.submitRenderJob()}</li>
 *   <li>Return the render job ID</li>
 * </ol>
 *
 * <h3>Configuration</h3>
 * <pre>{@code
 * render:
 *   execution:
 *     mode: local
 * }</pre>
 *
 * @see RenderExecutionPort
 * @see TemporalRenderExecutionAdapter
 */
@Component
@ConditionalOnProperty(prefix = "render.execution", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalRenderExecutionAdapter implements RenderExecutionPort {

    private static final Logger log = LoggerFactory.getLogger(LocalRenderExecutionAdapter.class);

    private final RenderOrchestratorPort orchestratorPort;

    /**
     * Creates a new LocalRenderExecutionAdapter.
     *
     * @param orchestratorPort the render orchestrator port (must not be null)
     */
    public LocalRenderExecutionAdapter(RenderOrchestratorPort orchestratorPort) {
        this.orchestratorPort = orchestratorPort;
    }

    /**
     * Execute a render job locally by delegating to the orchestrator port.
     *
     * @param renderJobId the unique identifier for the render job
     * @param tenantId    the tenant identifier
     * @param projectId   the project identifier
     * @param prompt      the AI prompt/script
     * @param profile     the render profile
     * @return the render job ID
     */
    @Override
    public String execute(String renderJobId, String tenantId, String projectId,
                          String prompt, String profile) {
        log.info("Executing render job locally: jobId={}, tenant={}, project={}",
                renderJobId, tenantId, projectId);

        SubmitRenderJobRequest request = new SubmitRenderJobRequest(
                tenantId, projectId, prompt, profile);

        return orchestratorPort.submitRenderJob(request);
    }
}

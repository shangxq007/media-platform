package com.example.platform.workflow.port;

/**
 * Port for executing render workflows.
 * 
 * <p>Abstracts the execution mechanism (local vs Temporal) from the domain logic.
 * Implementations handle the orchestration of render jobs through either
 * local orchestration or Temporal workflow engine.</p>
 * 
 * <h3>Architecture</h3>
 * <ul>
 *   <li><strong>Local adapter</strong>: Delegates to {@code RenderOrchestratorService} for direct execution</li>
 *   <li><strong>Temporal adapter</strong>: Starts Temporal workflows for distributed execution</li>
 * </ul>
 * 
 * <h3>Usage</h3>
 * <pre>{@code
 * @Service
 * public class RenderService {
 *     private final RenderExecutionPort executionPort;
 *     
 *     public RenderService(RenderExecutionPort executionPort) {
 *         this.executionPort = executionPort;
 *     }
 *     
 *     public String submitJob(String jobId, String tenantId, String projectId, 
 *                            String prompt, String profile) {
 *         return executionPort.execute(jobId, tenantId, projectId, prompt, profile);
 *     }
 * }
 * }</pre>
 * 
 * @see com.example.platform.workflow.adapter.LocalRenderExecutionAdapter
 * @see com.example.platform.workflow.adapter.TemporalRenderExecutionAdapter
 */
public interface RenderExecutionPort {

    /**
     * Execute a render job.
     *
     * @param renderJobId the unique identifier for the render job
     * @param tenantId    the tenant identifier for multi-tenant isolation
     * @param projectId   the project identifier the render job belongs to
     * @param prompt      the AI prompt/script for the render
     * @param profile     the render profile (e.g., "hd", "4k", "mobile")
     * @return the render job result identifier
     * @throws IllegalArgumentException if any required parameter is invalid
     * @throws IllegalStateException    if the render job cannot be executed
     */
    String execute(String renderJobId, String tenantId, String projectId,
                   String prompt, String profile);
}

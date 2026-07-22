package com.example.platform.render.api.port;

/**
 * Narrow port for resuming render workflow execution after job submission.
 *
 * <p>This port exists to break the circular dependency between
 * {@code LocalRenderSubmitContinuation} and {@code RenderOrchestratorPort}.
 * It exposes only the {@code executeExistingRenderJob} operation that the
 * workflow continuation needs, without coupling to the full orchestrator surface.</p>
 *
 * <h3>Dependency chain (no cycle):</h3>
 * <pre>
 * RenderOrchestratorService → RenderJobSubmitContinuation
 *     → LocalRenderSubmitContinuation → RenderWorkflowResumePort
 *         → RenderWorkflowResumeService → RenderJobExecutionService
 * </pre>
 */
public interface RenderWorkflowResumePort {

    /**
     * Resume execution of an existing render job.
     *
     * @param tenantId tenant identifier
     * @param jobId    existing render job id
     * @return the render job id
     */
    String executeExistingRenderJob(String tenantId, String jobId);
}

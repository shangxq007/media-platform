package com.example.platform.render.api.port;

import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.app.dto.ArtifactInfoResponse;

import java.util.List;

/**
 * Port for submitting render jobs and querying artifacts from the render orchestrator.
 *
 * <p>This interface is part of the render module's public API surface. Other
 * modules (such as workflow-module) should depend on this port rather than
 * directly on the internal {@code RenderOrchestratorService}.</p>
 *
 * <h3>Architecture</h3>
 * <ul>
 *   <li>The render module provides the implementation internally</li>
 *   <li>Other modules access render orchestration through this port only</li>
 *   <li>This follows ADR-002: port interfaces for cross-module service access</li>
 * </ul>
 *
 * @see com.example.platform.render.api.dto.SubmitRenderJobRequest
 */
public interface RenderOrchestratorPort {

    /**
     * Submit a render job for execution.
     *
     * @param request the render job submission request (must not be null)
     * @return the unique identifier of the created render job
     * @throws IllegalArgumentException if the request is invalid or quota is exceeded
     * @throws IllegalStateException    if the render job cannot be submitted
     */
    String submitRenderJob(SubmitRenderJobRequest request);

    /**
     * Execute an existing queued render job (loads timeline snapshot when present).
     *
     * @param tenantId tenant identifier
     * @param jobId    existing render job id
     * @return the render job id
     */
    String executeExistingRenderJob(String tenantId, String jobId);

    /**
     * Completes the render/storage phase for a job already in {@code RENDERING} with {@code ai_script} set.
     * Used by {@link com.example.platform.render.app.RenderNatronQueueProcessor} after deferred enqueue.
     */
    String finishRenderPhase(String tenantId, String jobId);

    /**
     * Get artifacts associated with a render job.
     *
     * @param jobId the render job identifier
     * @return list of artifacts for the job
     * @throws IllegalArgumentException if the job is not found or tenant access is denied
     */
    List<ArtifactInfoResponse> getArtifactsByJob(String jobId);
    byte[] getArtifactContent(String artifactId);

    /**
     * Loads Internal Timeline 1.0 JSON (or best available script) for edit-and-rerender flows.
     */
    String loadJobTimelineJson(String tenantId, String jobId);
}

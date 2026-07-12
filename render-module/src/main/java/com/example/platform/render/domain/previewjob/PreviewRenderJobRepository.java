package com.example.platform.render.domain.previewjob;

import java.util.List;
import java.util.Optional;

/**
 * Port interface for preview render job persistence.
 *
 * <p>This is a domain port — implementations live in the infrastructure layer.
 * The application service depends on this interface, not on concrete storage.</p>
 */
public interface PreviewRenderJobRepository {

    /**
     * Persist a new preview render job.
     *
     * @param job the job to save (must not be null)
     * @return the saved job (same as input for in-memory implementations)
     */
    PreviewRenderJob save(PreviewRenderJob job);

    /**
     * Find a preview render job by its ID.
     *
     * @param jobId the job identifier
     * @return the job, or empty if not found
     */
    Optional<PreviewRenderJob> findById(PreviewRenderJobId jobId);

    /**
     * Find a preview render job by ID, scoped to tenant and project.
     * Returns empty if not found or tenant/project mismatch.
     *
     * @param jobId     the job identifier
     * @param tenantId  the tenant identifier
     * @param projectId the project identifier
     * @return the job, or empty if not found or mismatch
     */
    Optional<PreviewRenderJob> findByIdAndTenantAndProject(
            PreviewRenderJobId jobId, String tenantId, String projectId);

    /**
     * List all preview render jobs for a project within a tenant.
     *
     * @param tenantId  the tenant identifier
     * @param projectId the project identifier
     * @param limit     maximum number of results
     * @return list of jobs (may be empty)
     */
    List<PreviewRenderJob> listByTenantAndProject(
            String tenantId, String projectId, int limit);

    /**
     * Update the status of a preview render job.
     *
     * @param jobId      the job identifier
     * @param newStatus  the new status
     * @param outputProductId the output product ID (nullable, set on COMPLETED)
     * @param errorMessage the error message (nullable, set on FAILED)
     */
    void updateStatus(PreviewRenderJobId jobId,
                      PreviewRenderJobStatus newStatus,
                      String outputProductId,
                      String errorMessage);
}

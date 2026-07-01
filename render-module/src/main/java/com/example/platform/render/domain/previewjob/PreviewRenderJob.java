package com.example.platform.render.domain.previewjob;

import java.time.Instant;
import java.util.Objects;

/**
 * Preview Render Job — lightweight render producing a preview output.
 *
 * <p>This aggregate root represents a single preview render job with its
 * lifecycle managed through deterministic state transitions. The preview
 * render pipeline uses FFmpeg/libass only; no Remotion, no Artifact DAG,
 * no Spring AI.</p>
 *
 * <p>Architecture boundaries:
 * <ul>
 *   <li>No internal provider/backend/environment selection exposed</li>
 *   <li>No local filesystem paths in any public representation</li>
 *   <li>No signed URLs or storage reference IDs exposed</li>
 *   <li>ProductRuntime/StorageRuntime boundaries preserved</li>
 *   <li>Fail-closed: invalid transitions throw</li>
 * </ul>
 *
 * @param jobId         unique job identifier
 * @param tenantId      owning tenant
 * @param projectId     owning project
 * @param snapshotId    timeline snapshot ID used for this render
 * @param profile       render profile (e.g. "default_1080p")
 * @param status        current lifecycle status
 * @param outputProductId output product ID (set when COMPLETED)
 * @param errorMessage  error detail (set when FAILED)
 * @param createdAt     creation timestamp
 * @param completedAt   completion timestamp (set when COMPLETED or FAILED)
 */
public record PreviewRenderJob(
        PreviewRenderJobId jobId,
        String tenantId,
        String projectId,
        String snapshotId,
        String profile,
        PreviewRenderJobStatus status,
        String outputProductId,
        String errorMessage,
        Instant createdAt,
        Instant completedAt
) {

    public PreviewRenderJob {
        Objects.requireNonNull(jobId, "jobId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(projectId, "projectId must not be null");
        Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /**
     * Factory for creating a new preview render job in QUEUED state.
     */
    public static PreviewRenderJob create(
            PreviewRenderJobId jobId,
            String tenantId,
            String projectId,
            String snapshotId,
            String profile) {
        return new PreviewRenderJob(
                jobId, tenantId, projectId, snapshotId, profile,
                PreviewRenderJobStatus.QUEUED,
                null, null, Instant.now(), null);
    }

    /**
     * Transition to EXECUTING state.
     *
     * @throws IllegalStateException if not in QUEUED state
     */
    public PreviewRenderJob startExecuting() {
        if (status != PreviewRenderJobStatus.QUEUED) {
            throw new IllegalStateException(
                    "Cannot start executing from state " + status);
        }
        return new PreviewRenderJob(
                jobId, tenantId, projectId, snapshotId, profile,
                PreviewRenderJobStatus.EXECUTING,
                null, null, createdAt, null);
    }

    /**
     * Transition to COMPLETED state with output product reference.
     *
     * @param outputProductId the output Product ID from ProductRuntime
     * @throws IllegalStateException if not in EXECUTING state
     */
    public PreviewRenderJob complete(String outputProductId) {
        if (status != PreviewRenderJobStatus.EXECUTING) {
            throw new IllegalStateException(
                    "Cannot complete from state " + status);
        }
        Objects.requireNonNull(outputProductId, "outputProductId must not be null");
        return new PreviewRenderJob(
                jobId, tenantId, projectId, snapshotId, profile,
                PreviewRenderJobStatus.COMPLETED,
                outputProductId, null, createdAt, Instant.now());
    }

    /**
     * Transition to FAILED state with error message.
     *
     * @param errorMessage the failure reason
     * @throws IllegalStateException if in a terminal state
     */
    public PreviewRenderJob fail(String errorMessage) {
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot fail from terminal state " + status);
        }
        return new PreviewRenderJob(
                jobId, tenantId, projectId, snapshotId, profile,
                PreviewRenderJobStatus.FAILED,
                null, errorMessage, createdAt, Instant.now());
    }

    /**
     * Transition to CANCELLED state.
     *
     * @throws IllegalStateException if in a terminal state or already EXECUTING
     */
    public PreviewRenderJob cancel() {
        if (status == PreviewRenderJobStatus.EXECUTING) {
            throw new IllegalStateException(
                    "Cannot cancel a job in EXECUTING state");
        }
        if (status.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot cancel from terminal state " + status);
        }
        return new PreviewRenderJob(
                jobId, tenantId, projectId, snapshotId, profile,
                PreviewRenderJobStatus.CANCELLED,
                null, null, createdAt, Instant.now());
    }

    /**
     * Returns true if this job is in a terminal state.
     */
    public boolean isTerminal() {
        return status.isTerminal();
    }
}

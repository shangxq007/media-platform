package com.example.platform.render.app.preview;

import com.example.platform.render.domain.previewjob.PreviewRenderJob;
import com.example.platform.render.domain.previewjob.PreviewRenderJobStatus;

import java.time.Instant;

/**
 * Response DTO for a preview render job.
 *
 * <p>Contains only safe, API-facing fields. Does not expose:
 * <ul>
 *   <li>Internal provider/backend/environment selection</li>
 *   <li>Local filesystem paths</li>
 *   <li>Storage reference IDs or signed URLs</li>
 *   <li>Provider selection internals</li>
 * </ul>
 */
public record PreviewRenderJobResponse(
        String jobId,
        String tenantId,
        String projectId,
        String snapshotId,
        String profile,
        String status,
        String outputProductId,
        String errorMessage,
        Instant createdAt,
        Instant completedAt
) {

    /**
     * Map from domain entity to API response.
     */
    public static PreviewRenderJobResponse fromDomain(PreviewRenderJob job) {
        return new PreviewRenderJobResponse(
                job.jobId().value(),
                job.tenantId(),
                job.projectId(),
                job.snapshotId(),
                job.profile(),
                job.status().name(),
                job.outputProductId(),
                job.errorMessage(),
                job.createdAt(),
                job.completedAt());
    }
}

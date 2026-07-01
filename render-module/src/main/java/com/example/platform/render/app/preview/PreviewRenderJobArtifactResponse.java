package com.example.platform.render.app.preview;

import java.util.List;

/**
 * Response containing product/artifact metadata for a completed preview render job.
 *
 * <p>Reconstructed from Product metadata and ProductDependency lineage.
 * Does not expose:
 * <ul>
 *   <li>Internal provider/backend/environment selection</li>
 *   <li>Local filesystem paths or signed URLs</li>
 *   <li>Storage reference IDs or storage details</li>
 *   <li>Provider selection internals</li>
 * </ul>
 *
 * @param renderJobId       the preview render job identifier
 * @param projectId         the project identifier
 * @param outputProductId   the output Product ID (from ProductRuntime)
 * @param productStatus     the Product lifecycle status
 * @param mimeType          MIME type of the output (e.g. "video/mp4")
 * @param outputFormat      output format (e.g. "mp4", "webm")
 * @param width             output width in pixels
 * @param height            output height in pixels
 * @param fps               output frame rate
 * @param durationSeconds   output duration in seconds
 * @param hasSubtitles      whether the output contains subtitles
 * @param inputProductIds   list of input Product IDs used
 * @param inputDependencyCount number of resolved input dependencies
 * @param createdAt         job creation timestamp (ISO-8601)
 * @param completedAt       job completion timestamp (ISO-8601)
 * @param message           human-readable status message
 */
public record PreviewRenderJobArtifactResponse(
        String renderJobId,
        String projectId,
        String outputProductId,
        String productStatus,
        String mimeType,
        String outputFormat,
        int width,
        int height,
        int fps,
        double durationSeconds,
        boolean hasSubtitles,
        List<String> inputProductIds,
        int inputDependencyCount,
        String createdAt,
        String completedAt,
        String message
) {}

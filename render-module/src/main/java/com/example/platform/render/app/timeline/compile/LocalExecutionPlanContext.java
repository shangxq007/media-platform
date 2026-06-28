package com.example.platform.render.app.timeline.compile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Context for a local execution plan run.
 *
 * <p>Internal only — carries all necessary context for step execution
 * without exposing in public APIs.</p>
 *
 * @param renderJobId      the render job identifier
 * @param tenantId         the tenant identifier
 * @param projectId        the project identifier
 * @param timelineRevisionId the timeline revision identifier
 * @param snapshotId       the snapshot identifier
 * @param timelineJson     the raw timeline JSON (for FFmpeg)
 * @param outputProfile    the output profile name
 * @param inputProductIds  resolved input product IDs
 * @param inputProductId   primary input product ID
 * @param outputProductId  output product ID (populated after registration)
 * @param storageRoot      local storage root path
 * @param outputDir        output directory path
 * @param outputFileName   output file name
 * @param width            output width
 * @param height           output height
 * @param fps              output frame rate
 * @param duration         output duration
 * @param hasSubtitles     whether timeline has subtitles
 * @param outputFormat     output format (e.g., "mp4")
 * @param metadata         additional context metadata
 */
public record LocalExecutionPlanContext(
        String renderJobId,
        String tenantId,
        String projectId,
        String timelineRevisionId,
        String snapshotId,
        String timelineJson,
        String outputProfile,
        List<String> inputProductIds,
        String inputProductId,
        String outputProductId,
        Path storageRoot,
        Path outputDir,
        String outputFileName,
        int width,
        int height,
        int fps,
        double duration,
        boolean hasSubtitles,
        String outputFormat,
        Map<String, String> metadata) {

    /**
     * Returns a safe summary string for logging (no secrets/paths).
     */
    public String summary() {
        return "LocalExecutionPlanContext[job=" + renderJobId
                + " project=" + projectId
                + " inputs=" + (inputProductIds != null ? inputProductIds.size() : 0)
                + " format=" + outputFormat
                + " " + width + "x" + height + "@" + fps + "]";
    }
}

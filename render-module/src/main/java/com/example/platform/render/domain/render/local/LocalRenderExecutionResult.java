package com.example.platform.render.domain.render.local;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of a local render execution bridged from a BasicRenderPlan.
 *
 * @param executionId      execution id
 * @param planId           originating BasicRenderPlan id
 * @param status           execution status
 * @param outputPath       path to output file (null if not produced)
 * @param outputFileBytes  size of output file
 * @param ffmpegExitCode   FFmpeg process exit code
 * @param ffmpegDuration   FFmpeg execution duration
 * @param ffprobeExitCode  ffprobe validation exit code
 * @param actualWidth      actual output width from ffprobe
 * @param actualHeight     actual output height from ffprobe
 * @param actualDurationSec actual output duration from ffprobe
 * @param actualCodec      actual codec from ffprobe
 * @param actualFormat     actual format from ffprobe
 * @param unsupportedSteps steps that were not supported
 * @param issues           execution issues
 * @param safeMetadata     safe metadata
 */
public record LocalRenderExecutionResult(
        LocalRenderExecutionId executionId,
        String planId,
        LocalRenderExecutionStatus status,
        Path outputPath,
        long outputFileBytes,
        int ffmpegExitCode,
        Duration ffmpegDuration,
        int ffprobeExitCode,
        int actualWidth,
        int actualHeight,
        double actualDurationSec,
        String actualCodec,
        String actualFormat,
        List<String> unsupportedSteps,
        List<LocalRenderSmokeIssue> issues,
        Map<String, String> safeMetadata
) {
    public LocalRenderExecutionResult {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(planId, "planId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        unsupportedSteps = unsupportedSteps == null ? List.of() : List.copyOf(unsupportedSteps);
        issues = issues == null ? List.of() : List.copyOf(issues);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}

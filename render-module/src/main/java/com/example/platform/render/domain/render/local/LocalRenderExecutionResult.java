package com.example.platform.render.domain.render.local;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of a local render execution bridged from a BasicRenderPlan.
 *
 * @param executionId          execution id
 * @param planId               originating BasicRenderPlan id
 * @param status               execution status
 * @param inputSourceKind      input source kind (null if synthetic testsrc)
 * @param inputSourceOrigin    input source origin (null if synthetic testsrc)
 * @param inputPath            path to input file (null if synthetic testsrc)
 * @param inputFileBytes       size of input file (0 if synthetic testsrc)
 * @param inputFfprobeExitCode ffprobe validation exit code for input (-1 if not validated)
 * @param inputWidth           actual input width from ffprobe (0 if not validated)
 * @param inputHeight          actual input height from ffprobe (0 if not validated)
 * @param inputDurationSec     actual input duration from ffprobe (0 if not validated)
 * @param inputCodec           actual input codec from ffprobe (null if not validated)
 * @param inputFormat          actual input format from ffprobe (null if not validated)
 * @param outputPath           path to output file (null if not produced)
 * @param outputFileBytes      size of output file
 * @param ffmpegExitCode       FFmpeg process exit code
 * @param ffmpegDuration       FFmpeg execution duration
 * @param ffprobeExitCode      ffprobe validation exit code for output
 * @param actualWidth          actual output width from ffprobe
 * @param actualHeight         actual output height from ffprobe
 * @param actualDurationSec    actual output duration from ffprobe
 * @param actualCodec          actual output codec from ffprobe
 * @param actualFormat         actual output format from ffprobe
 * @param unsupportedSteps     steps that were not supported
 * @param captionOverlayCount  number of caption overlays applied
 * @param issues               execution issues
 * @param safeMetadata         safe metadata
 */
public record LocalRenderExecutionResult(
        LocalRenderExecutionId executionId,
        String planId,
        LocalRenderExecutionStatus status,
        // Input source metadata
        LocalMediaSourceKind inputSourceKind,
        LocalMediaSourceOrigin inputSourceOrigin,
        Path inputPath,
        long inputFileBytes,
        int inputFfprobeExitCode,
        int inputWidth,
        int inputHeight,
        double inputDurationSec,
        String inputCodec,
        String inputFormat,
        // Output metadata
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
        // Other
        List<String> unsupportedSteps,
        int captionOverlayCount,
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

    /**
     * Returns true if this result has input source metadata (real media source).
     */
    public boolean hasInputSource() {
        return inputSourceKind != null;
    }
}

package com.example.platform.render.domain.render.local;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of a single local render smoke execution.
 */
public record LocalRenderSmokeResult(
        LocalRenderSmokeId smokeId,
        LocalRenderSmokeName smokeName,
        LocalRenderSmokeStatus status,
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
        List<LocalRenderSmokeIssue> issues,
        Map<String, String> safeMetadata
) {
    public LocalRenderSmokeResult {
        Objects.requireNonNull(smokeId, "smokeId must not be null");
        Objects.requireNonNull(smokeName, "smokeName must not be null");
        Objects.requireNonNull(status, "status must not be null");
        issues = issues == null ? List.of() : List.copyOf(issues);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }
}

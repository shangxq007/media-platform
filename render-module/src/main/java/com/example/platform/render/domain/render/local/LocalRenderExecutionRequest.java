package com.example.platform.render.domain.render.local;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Request to execute a BasicRenderPlan through the local runner bridge.
 *
 * @param executionId        unique execution id
 * @param planId             reference to the originating BasicRenderPlan id
 * @param width              output width from plan's output profile
 * @param height             output height from plan's output profile
 * @param durationSec        synthetic duration for testsrc input
 * @param fps                frame rate from plan's output profile
 * @param videoCodec         target video codec from plan
 * @param container          target container from plan
 * @param outputRoot         root directory for output
 * @param unsupportedSteps   steps that were detected as unsupported
 * @param captionOverlaySpecs safe caption overlay specs extracted from plan (empty if none)
 * @param safeMetadata       safe metadata from plan
 */
public record LocalRenderExecutionRequest(
        LocalRenderExecutionId executionId,
        String planId,
        int width,
        int height,
        double durationSec,
        int fps,
        String videoCodec,
        String container,
        Path outputRoot,
        List<String> unsupportedSteps,
        List<LocalCaptionOverlaySpec> captionOverlaySpecs,
        Map<String, String> safeMetadata
) {
    public LocalRenderExecutionRequest {
        Objects.requireNonNull(executionId, "executionId must not be null");
        Objects.requireNonNull(planId, "planId must not be null");
        Objects.requireNonNull(videoCodec, "videoCodec must not be null");
        Objects.requireNonNull(container, "container must not be null");
        Objects.requireNonNull(outputRoot, "outputRoot must not be null");
        unsupportedSteps = unsupportedSteps == null ? List.of() : List.copyOf(unsupportedSteps);
        captionOverlaySpecs = captionOverlaySpecs == null ? List.of() : List.copyOf(captionOverlaySpecs);
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
        if (width <= 0) throw new IllegalArgumentException("width must be positive");
        if (height <= 0) throw new IllegalArgumentException("height must be positive");
        if (durationSec <= 0) throw new IllegalArgumentException("durationSec must be positive");
        if (fps <= 0) throw new IllegalArgumentException("fps must be positive");
    }
}

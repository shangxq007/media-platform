package com.example.platform.render.domain.render.local;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Request to execute a local render smoke scenario.
 *
 * @param smokeId      unique smoke execution id
 * @param smokeName    human-readable scenario name
 * @param width        output width in pixels
 * @param height       output height in pixels
 * @param durationSec  output duration in seconds
 * @param fps          output frame rate
 * @param outputRoot   root directory for smoke output
 */
public record LocalRenderSmokeRequest(
        LocalRenderSmokeId smokeId,
        LocalRenderSmokeName smokeName,
        int width,
        int height,
        double durationSec,
        int fps,
        Path outputRoot
) {
    public LocalRenderSmokeRequest {
        Objects.requireNonNull(smokeId, "smokeId must not be null");
        Objects.requireNonNull(smokeName, "smokeName must not be null");
        Objects.requireNonNull(outputRoot, "outputRoot must not be null");
        if (width <= 0) throw new IllegalArgumentException("width must be positive");
        if (height <= 0) throw new IllegalArgumentException("height must be positive");
        if (durationSec <= 0) throw new IllegalArgumentException("durationSec must be positive");
        if (fps <= 0) throw new IllegalArgumentException("fps must be positive");
    }

    /**
     * Creates the default testsrc-h264-mp4 smoke request.
     */
    public static LocalRenderSmokeRequest testsrcH264Mp4(Path outputRoot) {
        return new LocalRenderSmokeRequest(
                LocalRenderSmokeId.generate(),
                new LocalRenderSmokeName("local-smoke-001-testsrc-h264-mp4"),
                320, 180, 2.0, 30,
                outputRoot
        );
    }
}

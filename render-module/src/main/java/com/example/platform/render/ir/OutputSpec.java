package com.example.platform.render.ir;

import java.util.Map;
import java.util.Objects;

/**
 * Specification for a render output: container format, codec, dimensions, frame rate.
 */
public record OutputSpec(
    String id,
    String container,
    String videoCodec,
    int width,
    int height,
    RationalTime frameRate,
    Map<String, Object> extensions
) {
    public OutputSpec {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(container, "container must not be null");
        Objects.requireNonNull(videoCodec, "videoCodec must not be null");
    }
}

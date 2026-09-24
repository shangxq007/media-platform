package com.example.platform.artifact.domain.typed;

import java.time.Duration;

/** Typed technical facts; absent facts remain absent and validation fails when required. */
public record ArtifactTechnicalProperties(Duration duration, Integer width, Integer height, Integer pageCount, String codec, String language, String locale) {
    public ArtifactTechnicalProperties {
        if (duration != null && duration.isNegative()) throw new IllegalArgumentException("duration must be non-negative");
        if (width != null && width <= 0 || height != null && height <= 0 || pageCount != null && pageCount <= 0) throw new IllegalArgumentException("technical dimensions must be positive");
    }
}

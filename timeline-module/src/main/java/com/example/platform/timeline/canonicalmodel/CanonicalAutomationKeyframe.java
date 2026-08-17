package com.example.platform.timeline.canonicalmodel;

import com.example.platform.shared.time.MediaTime;
import java.util.Objects;

/**
 * Canonical automation keyframe: stable identity, exact MediaTime position,
 * authored value, interpolation mode (HOLD/LINEAR).
 */
public record CanonicalAutomationKeyframe(
        String keyframeId,
        MediaTime time,
        double value,
        String interpolation) {

    public CanonicalAutomationKeyframe {
        Objects.requireNonNull(keyframeId, "keyframeId");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(interpolation, "interpolation");
        if (keyframeId.isBlank()) throw new IllegalArgumentException("keyframeId must not be blank");
    }
}

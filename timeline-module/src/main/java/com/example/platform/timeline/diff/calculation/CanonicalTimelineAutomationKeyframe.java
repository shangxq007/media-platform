package com.example.platform.timeline.diff.calculation;

import com.example.platform.shared.time.MediaTime;
import java.util.Objects;

/**
 * Canonical automation keyframe snapshot — exact MediaTime position, authored
 * value, interpolation mode.
 */
public record CanonicalTimelineAutomationKeyframe(
        String keyframeId,
        MediaTime time,
        double value,
        String interpolation) {

    public CanonicalTimelineAutomationKeyframe {
        Objects.requireNonNull(keyframeId, "keyframeId");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(interpolation, "interpolation");
    }
}

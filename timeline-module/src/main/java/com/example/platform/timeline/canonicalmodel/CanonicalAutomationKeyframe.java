package com.example.platform.timeline.canonicalmodel;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.MediaTimeJsonCodec;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Objects;

/**
 * Canonical automation keyframe: stable identity, exact MediaTime position,
 * authored value, interpolation mode (HOLD/LINEAR).
 */
public record CanonicalAutomationKeyframe(
        String keyframeId,
        @JsonSerialize(using = MediaTimeJsonCodec.Serializer.class)
        @JsonDeserialize(using = MediaTimeJsonCodec.Deserializer.class)
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

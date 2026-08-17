package com.example.platform.timeline.canonicalmodel;

import com.example.platform.shared.time.MediaTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Canonical Timeline-authored automation curve (TIMELINE_EFFECT_TRANSITION
 * C7/C8): stable identity, target entity, parameter path, value type, exact
 * MediaTime keyframes with deterministic ordering (duplicate-time rejected),
 * HOLD/LINEAR interpolation, extrapolation. No wall-clock semantics.
 */
public record CanonicalAutomationCurve(
        String automationId,
        String targetEntityId,
        String parameterPath,
        String valueType,
        String extrapolation,
        List<CanonicalAutomationKeyframe> keyframes) {

    public CanonicalAutomationCurve {
        Objects.requireNonNull(automationId, "automationId");
        Objects.requireNonNull(targetEntityId, "targetEntityId");
        Objects.requireNonNull(parameterPath, "parameterPath");
        Objects.requireNonNull(valueType, "valueType");
        Objects.requireNonNull(extrapolation, "extrapolation");
        if (automationId.isBlank()) throw new IllegalArgumentException("automationId must not be blank");
        if (parameterPath.isBlank()) throw new IllegalArgumentException("parameterPath must not be blank");
        if (valueType.isBlank()) throw new IllegalArgumentException("valueType must not be blank");
        keyframes = normalizeKeyframes(keyframes);
    }

    private static List<CanonicalAutomationKeyframe> normalizeKeyframes(List<CanonicalAutomationKeyframe> input) {
        if (input == null || input.isEmpty()) return List.of();
        List<CanonicalAutomationKeyframe> sorted = new ArrayList<>(input);
        sorted.sort(Comparator.comparing(CanonicalAutomationKeyframe::time));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).time().isEqualTo(sorted.get(i - 1).time())) {
                throw new IllegalArgumentException("Duplicate keyframe time: " + sorted.get(i).time());
            }
        }
        return List.copyOf(sorted);
    }

    /** Canonical serialization key used for deterministic ordering. */
    public String canonicalKey() {
        return automationId;
    }
}

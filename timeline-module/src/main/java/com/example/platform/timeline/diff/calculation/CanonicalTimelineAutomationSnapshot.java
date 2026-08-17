package com.example.platform.timeline.diff.calculation;

import com.example.platform.shared.time.MediaTime;
import java.util.List;
import java.util.Objects;

/**
 * Canonical automation snapshot — Timeline-authored temporal semantics visible
 * to the production merge path (TIMELINE_EFFECT_TRANSITION C7/C8): exact
 * MediaTime keyframes, deterministic ordering, HOLD/LINEAR. No wall clock.
 */
public record CanonicalTimelineAutomationSnapshot(
        String automationId,
        String targetEntityId,
        String parameterPath,
        String valueType,
        String extrapolation,
        List<CanonicalTimelineAutomationKeyframe> keyframes) {

    public CanonicalTimelineAutomationSnapshot {
        Objects.requireNonNull(automationId, "automationId");
        keyframes = keyframes == null ? List.of() : List.copyOf(keyframes);
        if (automationId.isBlank()) throw new IllegalArgumentException("automationId must not be blank");
    }

    /** Merge-relevant local semantic equality. */
    public boolean localSemanticsEquals(CanonicalTimelineAutomationSnapshot other) {
        return Objects.equals(targetEntityId, other.targetEntityId)
                && Objects.equals(parameterPath, other.parameterPath)
                && Objects.equals(valueType, other.valueType)
                && Objects.equals(extrapolation, other.extrapolation)
                && keyframes.equals(other.keyframes);
    }
}

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
        return semanticFingerprint().equals(other.semanticFingerprint());
    }

    /**
     * THIRD CORRECTION (complete semantic signature): deterministic fingerprint
     * over ALL merge-relevant authored fields — targetEntityId/parameterPath/
     * valueType/extrapolation/ordered keyframes (id/time/value/interpolation).
     * Used by equality, diff afterValue, and merge conflict identity from ONE
     * authority. No wall-clock semantics.
     */
    public String semanticFingerprint() {
        StringBuilder sb = new StringBuilder();
        sb.append("target=").append(targetEntityId == null ? "" : targetEntityId).append(';')
          .append("path=").append(parameterPath == null ? "" : parameterPath).append(';')
          .append("type=").append(valueType == null ? "" : valueType).append(';')
          .append("extrap=").append(extrapolation == null ? "" : extrapolation).append(';')
          .append("kf=");
        for (CanonicalTimelineAutomationKeyframe k : keyframes) {
            sb.append(k.keyframeId() == null ? "" : k.keyframeId()).append(':')
              .append(k.time() == null ? "" : k.time().ticks() + "/" + k.time().timeScale()).append(':')
              .append(k.value()).append(':')
              .append(k.interpolation() == null ? "" : k.interpolation()).append(';');
        }
        return sb.toString();
    }
}

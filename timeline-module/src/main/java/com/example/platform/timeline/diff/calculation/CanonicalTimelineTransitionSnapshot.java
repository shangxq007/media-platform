package com.example.platform.timeline.diff.calculation;

import com.example.platform.shared.time.MediaTime;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical transition snapshot — first-class relationship state visible to the
 * production merge diff/plan/apply path (TIMELINE_EFFECT_TRANSITION C9/C10).
 * Timeline owns topology; local semantics (definition, duration, alignment,
 * temporal policy, parameters) live here.
 */
public record CanonicalTimelineTransitionSnapshot(
        String transitionId,
        String transitionDefinitionId,
        String transitionDefinitionVersion,
        String outgoingClipId,
        String incomingClipId,
        String mediaType,
        MediaTime duration,
        String alignment,
        String temporalPolicy,
        Map<String, String> parameters) {

    public CanonicalTimelineTransitionSnapshot {
        Objects.requireNonNull(transitionId, "transitionId");
        Objects.requireNonNull(duration, "duration");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        if (transitionId.isBlank()) throw new IllegalArgumentException("transitionId must not be blank");
    }

    /** Merge-relevant local semantic equality (excludes identity). */
    public boolean localSemanticsEquals(CanonicalTimelineTransitionSnapshot other) {
        return semanticFingerprint().equals(other.semanticFingerprint());
    }

    /**
     * THIRD CORRECTION (complete semantic signature): deterministic fingerprint
     * over ALL merge-relevant authored fields — definition/version/participants/
     * mediaType/duration/alignment/temporalPolicy/parameters (sorted keys).
     * Used by equality, diff afterValue, and merge conflict identity from ONE
     * authority. Provider/runtime fields excluded.
     */
    public String semanticFingerprint() {
        StringBuilder sb = new StringBuilder();
        sb.append("def=").append(transitionDefinitionId == null ? "" : transitionDefinitionId).append(';')
          .append("ver=").append(transitionDefinitionVersion == null ? "" : transitionDefinitionVersion).append(';')
          .append("out=").append(outgoingClipId == null ? "" : outgoingClipId).append(';')
          .append("inc=").append(incomingClipId == null ? "" : incomingClipId).append(';')
          .append("media=").append(mediaType == null ? "" : mediaType).append(';')
          .append("dur=").append(duration == null ? "" : duration.ticks() + "/" + duration.timeScale()).append(';')
          .append("align=").append(alignment == null ? "" : alignment).append(';')
          .append("pol=").append(temporalPolicy == null ? "" : temporalPolicy).append(';')
          .append("params=");
        if (parameters != null && !parameters.isEmpty()) {
            new java.util.TreeMap<>(parameters).forEach((k, v) ->
                    sb.append(k).append('=').append(v == null ? "" : v).append(','));
        }
        return sb.toString();
    }
}

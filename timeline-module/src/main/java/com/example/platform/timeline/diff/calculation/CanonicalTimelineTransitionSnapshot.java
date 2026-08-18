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
        // CHECKPOINT_A Round 3: local semantics owned by TransitionCanonicalSemantics.
        return com.example.platform.timeline.semantics.transition.TransitionCanonicalSemantics
                .localSemanticsEquals(this, other);
    }

    /**
     * COMPONENT_LOCAL_SEMANTIC_AUTHORITY_V1 (CHECKPOINT_A Round 3): deterministic
     * fingerprint delegated to the Transition-local authority (SHA-256 over the
     * canonical JSON value — no delimiter grammar, no collision).
     */
    public String semanticFingerprint() {
        return com.example.platform.timeline.semantics.transition.TransitionCanonicalSemantics
                .semanticFingerprint(this);
    }
}

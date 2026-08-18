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
        // CHECKPOINT_A Round 3: local semantics owned by AutomationCanonicalSemantics.
        return com.example.platform.timeline.semantics.automation.AutomationCanonicalSemantics
                .localSemanticsEquals(this, other);
    }

    /**
     * COMPONENT_LOCAL_SEMANTIC_AUTHORITY_V1 (CHECKPOINT_A Round 3): deterministic
     * fingerprint delegated to the Automation-local authority (SHA-256 over the
     * canonical JSON value — no delimiter grammar, no collision).
     */
    public String semanticFingerprint() {
        return com.example.platform.timeline.semantics.automation.AutomationCanonicalSemantics
                .semanticFingerprint(this);
    }
}

package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticBinding;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP20 correction R4-A1: immutable integrity-bound authored EFFECT
 * semantic snapshot.
 *
 * <p>Repository reality (R3/R4 effect authority review): authored effect state
 * is Timeline semantics — the wire timeline JSON carries {@code clip.effects[]}
 * (canonical {@code TimelineClipEffect} authority with
 * {@code EffectCanonicalSemantics} encoding); {@code EffectInstance} has a
 * stable {@code effectInstanceId} and {@code EffectDefinition} has stable
 * {@code definitionId} + {@code version}. The canonical
 * {@code TimelineDocument} projection does not carry effects (incomplete
 * persistence projection, E9), so effects are modeled here as an
 * independently-bound immutable semantic snapshot rather than being forced into
 * TimelineDocument.
 *
 * <p>Construction is RESTRICTED (private constructor); the ONLY public path is
 * {@link VerifiedEffectSemanticSnapshotFactory#verified(List, List, EffectSemanticBinding)}
 * which enforces definition reference/version integrity AND recomputes the
 * authoritative {@link EffectSemanticBinding} digest via the single
 * Timeline/Effect domain authority, failing closed on mismatch (R4-A1).
 *
 * <p>The authoritative {@link EffectSemanticBinding} is retained: the final
 * {@link RenderPlan} carries it as an {@link EffectSemanticReference}
 * (R4-A2), participates in the canonical fingerprint (R4-A3), and is explained
 * in provenance (R4-A4).
 */
public final class VerifiedEffectSemanticSnapshot {

    private final List<EffectInstance> effects;
    private final List<EffectInstance.EffectDefinition> effectDefinitions;
    private final ContentDigest contentPin;
    private final EffectSemanticBinding binding;

    private VerifiedEffectSemanticSnapshot(
            List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> effectDefinitions,
            ContentDigest contentPin,
            EffectSemanticBinding binding) {
        this.effects = List.copyOf(effects);
        this.effectDefinitions = List.copyOf(effectDefinitions);
        this.contentPin = Objects.requireNonNull(contentPin, "contentPin");
        this.binding = Objects.requireNonNull(binding, "binding");
    }

    /** Factory-only construction (see {@link VerifiedEffectSemanticSnapshotFactory}). */
    static VerifiedEffectSemanticSnapshot create(
            List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> effectDefinitions,
            ContentDigest contentPin,
            EffectSemanticBinding binding) {
        return new VerifiedEffectSemanticSnapshot(effects, effectDefinitions, contentPin, binding);
    }

    /** Immutable typed effect instances (all definitions verified present/versioned). */
    public List<EffectInstance> effects() {
        return effects;
    }

    /** Immutable typed effect definition catalog bound to this snapshot. */
    public List<EffectInstance.EffectDefinition> effectDefinitions() {
        return effectDefinitions;
    }

    /**
     * Value-bound content pin covering the complete authored effect semantic
     * state (instances + definitions, semantic fields only — provenance fields
     * excluded per R4-A5). Computed by the single Timeline/Effect domain
     * authority. Two snapshots with the same semantic effect state always
     * produce the same pin; different state always produces a different pin.
     */
    public ContentDigest contentPin() {
        return contentPin;
    }

    /** The authoritative authored binding (revision identity + digest). */
    public EffectSemanticBinding binding() {
        return binding;
    }

    /** The typed reference carried into the final RenderPlan. */
    public EffectSemanticReference toReference() {
        return new EffectSemanticReference(binding);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VerifiedEffectSemanticSnapshot s)) {
            return false;
        }
        return contentPin.equals(s.contentPin);
    }

    @Override
    public int hashCode() {
        return contentPin.hashCode();
    }

    @Override
    public String toString() {
        return "VerifiedEffectSemanticSnapshot(effects=" + effects.size()
                + ", definitions=" + effectDefinitions.size() + ", pin=" + contentPin + ")";
    }
}

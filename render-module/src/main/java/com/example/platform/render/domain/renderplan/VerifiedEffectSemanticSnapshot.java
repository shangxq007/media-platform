package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP20 correction R3-B1: immutable integrity-bound authored EFFECT
 * semantic snapshot.
 *
 * <p>Repository reality (R3 effect authority review): authored effect state is
 * Timeline semantics — the wire timeline JSON carries {@code clip.effects[]}
 * (canonical {@code TimelineClipEffect} authority with
 * {@code EffectCanonicalSemantics} encoding); {@code EffectInstance} has a
 * stable {@code effectInstanceId} and {@code EffectDefinition} has stable
 * {@code definitionId} + {@code version}. The canonical
 * {@code TimelineDocument} projection does not carry effects (incomplete
 * persistence projection, E9), so effects are modeled here as an
 * independently-pinned immutable semantic snapshot rather than being forced
 * into TimelineDocument.
 *
 * <p>Construction is RESTRICTED (private constructor); the ONLY public path is
 * {@link VerifiedEffectSemanticSnapshotFactory#verified(List, List)} which:
 * <ol>
 *   <li>fails closed when an effect references an unknown
 *       {@code effectDefinitionId},</li>
 *   <li>fails closed when an effect's {@code effectDefinitionVersion} does not
 *       match the referenced definition's version,</li>
 *   <li>computes a deterministic content pin (SHA-256 of the explicit
 *       value-ordered canonical encoding of the complete typed effect state),
 *       so the pin is value-bound, not identity-bound,</li>
 *   <li>returns an immutable typed snapshot.</li>
 * </ol>
 *
 * <p>The pure render planner can therefore never consume
 * {@code VerifiedTimelineRevision R1 + arbitrary caller-supplied effects from
 * R2}: the effect state is an immutable verified component whose pin covers
 * every authored effect semantic field contributing to plan WHAT.
 */
public final class VerifiedEffectSemanticSnapshot {

    private final List<EffectInstance> effects;
    private final List<EffectInstance.EffectDefinition> effectDefinitions;
    private final ContentDigest contentPin;

    private VerifiedEffectSemanticSnapshot(
            List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> effectDefinitions,
            ContentDigest contentPin) {
        this.effects = List.copyOf(effects);
        this.effectDefinitions = List.copyOf(effectDefinitions);
        this.contentPin = Objects.requireNonNull(contentPin, "contentPin");
    }

    /** Factory-only construction (see {@link VerifiedEffectSemanticSnapshotFactory}). */
    static VerifiedEffectSemanticSnapshot create(
            List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> effectDefinitions,
            ContentDigest contentPin) {
        return new VerifiedEffectSemanticSnapshot(effects, effectDefinitions, contentPin);
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
     * state (every instance id/definition id/version/parameters/range/flag).
     * Two snapshots with the same semantic effect state always produce the same
     * pin; different state always produces a different pin.
     */
    public ContentDigest contentPin() {
        return contentPin;
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

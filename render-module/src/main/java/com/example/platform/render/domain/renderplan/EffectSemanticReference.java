package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.timeline.semantics.effect.EffectSemanticBinding;
import java.util.Objects;

/**
 * ROADMAP20 correction R4-A2: authoritative authored Effect semantic
 * reference carried by the FINAL Logical RenderPlan.
 *
 * <p>Typed, immutable, provider-neutral reference to the authored Effect
 * semantic state consumed by this plan. It is NOT a transient planning-input
 * value: the final {@link RenderPlan} retains it, it participates in the
 * canonical RenderPlan fingerprint (R4-A3), and it is explained in the plan
 * provenance (R4-A4).
 *
 * <p>Fields (R4-A2 minimum):
 * <ul>
 *   <li>{@code binding} — the authoritative {@link EffectSemanticBinding}
 *       (revision identity + immutable Effect semantic content digest +
 *       semantic contract version), produced by the Timeline/Effect domain
 *       authority ({@code EffectSemanticBinding.of}).</li>
 * </ul>
 *
 * <p>This is a canonical semantic input: the fingerprint includes
 * {@link #semanticContractVersion()} and {@link #effectStateDigest()}, so any
 * authored Effect semantic change (application range, definition version,
 * automation binding, enabled state, parameters) changes the plan fingerprint
 * even when the materialized node structure happens to stay identical.
 */
public record EffectSemanticReference(EffectSemanticBinding binding) {

    public EffectSemanticReference {
        Objects.requireNonNull(binding, "binding");
    }

    /** The authored revision this Effect semantic state is bound to. */
    public String revisionId() {
        return binding.revisionId();
    }

    /** Immutable content digest of the authored Effect semantic state. */
    public ContentDigest effectStateDigest() {
        return binding.effectStateDigest();
    }

    /** Effect semantic contract version (schema identity). */
    public String semanticContractVersion() {
        return binding.semanticContractVersion();
    }
}

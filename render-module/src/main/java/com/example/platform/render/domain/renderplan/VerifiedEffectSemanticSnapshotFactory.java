package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticBinding;
import com.example.platform.timeline.semantics.effect.EffectSemanticStateCanonicalSemantics;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP20 correction R4-A1/R4-C: verification factory for the immutable
 * authored effect semantic snapshot.
 *
 * <p>This is the ONLY public construction path for
 * {@link VerifiedEffectSemanticSnapshot}. Integrity checks are REAL (not
 * caller-trust documentation):
 * <ul>
 *   <li>every {@code EffectInstance.effectDefinitionId} must resolve to a
 *       definition in the supplied catalog (else FAIL CLOSED),</li>
 *   <li>every {@code EffectInstance.effectDefinitionVersion} must equal the
 *       referenced definition's version (else FAIL CLOSED),</li>
 *   <li>the authoritative {@link EffectSemanticBinding} is recomputed by the
 *       single Timeline/Effect domain authority
 *       ({@link EffectSemanticStateCanonicalSemantics}) over the supplied typed
 *       state and must equal the caller-provided binding's digest AND revision
 *       id (else FAIL CLOSED) — this prevents verified timeline revision R1 +
 *       unrelated but internally-valid effect state R2 from passing the
 *       boundary,</li>
 *   <li>the content pin is the authoritative binding digest (value-bound:
 *       semantic-equal reconstructed state yields the same digest; distinct
 *       state yields a distinct digest).</li>
 * </ul>
 *
 * <p>R4-C: this factory performs NO Effect domain semantic grammar of its own —
 * the complete typed Effect semantic encoding lives in the Timeline/Effect
 * domain authority. Render consumes that authority only.
 */
public final class VerifiedEffectSemanticSnapshotFactory {

    private VerifiedEffectSemanticSnapshotFactory() {
    }

    /**
     * Verifies and pins the authored effect semantic state against an
     * authoritative binding.
     *
     * @param effects           typed effect instances (authored WHAT)
     * @param effectDefinitions effect definition catalog
     * @param binding           authoritative Effect semantic binding
     *                          (revision id + digest, domain-computed)
     * @return immutable verified effect semantic snapshot
     * @throws IllegalArgumentException on unknown definition reference, version
     *                                  mismatch, or binding digest/revision
     *                                  mismatch (fail closed)
     */
    public static VerifiedEffectSemanticSnapshot verified(
            List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> effectDefinitions,
            EffectSemanticBinding binding) {
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(effectDefinitions, "effectDefinitions");
        Objects.requireNonNull(binding, "binding");

        // 1/2. definition reference + version integrity (fail closed).
        for (EffectInstance effect : effects) {
            EffectInstance.EffectDefinition definition = effectDefinitions.stream()
                    .filter(d -> d.definitionId().equals(effect.effectDefinitionId()))
                    .findFirst()
                    .orElse(null);
            if (definition == null) {
                throw new IllegalArgumentException(
                        "Effect " + effect.effectInstanceId() + " references unknown "
                                + "effectDefinitionId '" + effect.effectDefinitionId() + "'");
            }
            if (!definition.version().equals(effect.effectDefinitionVersion())) {
                throw new IllegalArgumentException(
                        "Effect " + effect.effectInstanceId() + " version mismatch: "
                                + "instance requests '" + effect.effectDefinitionVersion()
                                + "' but definition '" + effect.effectDefinitionId()
                                + "' is version '" + definition.version() + "'");
            }
        }

        // 3. authoritative binding recomputation (single domain authority).
        EffectSemanticBinding recomputed = EffectSemanticBinding.of(
                binding.revisionId(), effects, effectDefinitions);
        if (!recomputed.effectStateDigest().equals(binding.effectStateDigest())) {
            throw new IllegalArgumentException(
                    "Effect semantic binding digest mismatch (R4-A1): supplied effect state "
                            + "does not match the authoritative binding for revision "
                            + binding.revisionId() + " — cross-revision/context effect "
                            + "assembly is rejected");
        }

        // 4. pin = authoritative binding digest (value-bound).
        ContentDigest pin = binding.effectStateDigest();
        return VerifiedEffectSemanticSnapshot.create(
                effects, effectDefinitions, pin, binding);
    }
}

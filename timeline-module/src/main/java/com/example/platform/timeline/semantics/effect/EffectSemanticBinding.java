package com.example.platform.timeline.semantics.effect;

import com.example.platform.shared.digest.ContentDigest;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP20 correction R4-A1: immutable authored Effect semantic binding.
 *
 * <p>Binds ONE authored Timeline revision identity to ONE immutable authored
 * Effect semantic state digest, computed by the Timeline/Effect domain
 * authority ({@link EffectSemanticStateCanonicalSemantics}). The binding is a
 * typed immutable value owned by the Effect domain — it is NOT a caller-made
 * hash over arbitrary lists. Construction is restricted to
 * {@link #of(String, List, List)} which delegates digest computation to the
 * single domain authority.
 *
 * <p>Purpose: the render planning boundary can prove WHICH timeline revision
 * is being planned and WHICH immutable Effect semantic state is being planned,
 * and that the two are authoritatively bound — verified Timeline revision R1
 * + unrelated but internally-valid Effect state R2 cannot pass a verification
 * boundary that checks {@code binding.revisionId()} against the planned
 * revision and recomputes the digest against the supplied projection.
 *
 * <p>Contract version: {@code effect-semantics-v1}. The digest covers the
 * complete authored Effect semantic state (instances + definitions, semantic
 * fields only; provenance fields excluded per R4-A5).
 */
public record EffectSemanticBinding(
        String revisionId,
        ContentDigest effectStateDigest,
        String semanticContractVersion) {

    public static final String CONTRACT_VERSION = "effect-semantics-v1";

    public EffectSemanticBinding {
        Objects.requireNonNull(revisionId, "revisionId");
        Objects.requireNonNull(effectStateDigest, "effectStateDigest");
        Objects.requireNonNull(semanticContractVersion, "semanticContractVersion");
        if (revisionId.isBlank()) {
            throw new IllegalArgumentException("revisionId must not be blank");
        }
    }

    /**
     * Creates the authoritative binding: the digest is computed by the single
     * Effect domain canonical authority over the complete typed semantic state.
     */
    public static EffectSemanticBinding of(
            String revisionId,
            List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> effectDefinitions) {
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(effectDefinitions, "effectDefinitions");
        String canonical = EffectSemanticStateCanonicalSemantics.canonicalEffectState(
                effects, effectDefinitions);
        ContentDigest digest = ContentDigest.sha256(
                EffectSemanticStateCanonicalSemantics.sha256Hex(canonical));
        return new EffectSemanticBinding(revisionId, digest, CONTRACT_VERSION);
    }
}

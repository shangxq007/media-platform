package com.example.platform.render.domain.renderplan;

import com.example.platform.timeline.semantics.effect.EffectDefinitionCanonicalSemantics;
import com.example.platform.timeline.semantics.effect.EffectSemanticEntry;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotCanonicalSemantics;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import java.util.Objects;

/**
 * ROADMAP20 final implementation: the ONLY public construction path for
 * {@link VerifiedEffectSemanticSnapshot} — verification against the revision's
 * EXACT Effect snapshot pin (RENDER_CONSUMES_VERIFIED_EFFECT_SNAPSHOT_NOT_CALLER_EFFECT_LISTS_V1).
 *
 * <p>Verifier checks (all fail closed):
 * <ol>
 *   <li>binding integrity — supplied snapshot id == expected pin snapshotId
 *       (RP1/BI2: semantically identical but different-id snapshot is REJECTED
 *       for an existing historical pin),</li>
 *   <li>semantic integrity — supplied contentDigest == expected pin contentDigest
 *       AND recomputed canonical digest == expected contentDigest (RP2/BI3:
 *       tampered content fails even with an unchanged reference),</li>
 *   <li>contract version — supplied version == expected pin version (BI5),</li>
 *   <li>structural integrity — every embedded definition digest verifies and
 *       every entry satisfies the V1 automation rule.</li>
 * </ol>
 *
 * <p>This factory performs NO Effect semantic grammar of its own — the single
 * canonical encoders live in the Timeline/Effect domain authority
 * ({@link EffectSemanticSnapshotCanonicalSemantics} /
 * {@link EffectDefinitionCanonicalSemantics}); Render only verifies.
 */
public final class VerifiedEffectSemanticSnapshotFactory {

    private VerifiedEffectSemanticSnapshotFactory() {
    }

    /**
     * Verifies the supplied snapshot against the revision's exact pin.
     *
     * @param snapshot          the immutable Effect semantic snapshot to verify
     * @param expectedReference the EXACT reference pinned by the Timeline
     *                          revision (from the revision's semantic context)
     * @param revisionId        the owning Timeline revision id
     * @return verified effect semantic snapshot
     * @throws IllegalArgumentException on any verification failure (fail closed)
     */
    public static VerifiedEffectSemanticSnapshot verified(
            EffectSemanticSnapshot snapshot,
            EffectSemanticSnapshotReference expectedReference,
            String revisionId) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(expectedReference, "expectedReference");
        Objects.requireNonNull(revisionId, "revisionId");

        // 1. binding integrity: exact snapshot id (RP1, BI2, RP3-C).
        if (!snapshot.id().equals(expectedReference.snapshotId())) {
            throw new IllegalArgumentException(
                    "Effect snapshot binding mismatch (RP1/BI2): revision pins snapshot '"
                            + expectedReference.snapshotId().value() + "' but supplied snapshot is '"
                            + snapshot.id().value() + "' — semantic equivalence does not authorize "
                            + "historical binding substitution");
        }
        // 2. semantic integrity: stored digest == pin digest.
        if (!snapshot.contentDigest().equals(expectedReference.contentDigest())) {
            throw new IllegalArgumentException(
                    "Effect snapshot digest mismatch (RP2): stored digest '"
                            + snapshot.contentDigest() + "' != pinned digest '"
                            + expectedReference.contentDigest() + "'");
        }
        // 3. contract version (BI5).
        if (!snapshot.semanticContractVersion().equals(expectedReference.semanticContractVersion())) {
            throw new IllegalArgumentException(
                    "Effect snapshot contract version mismatch (BI5): snapshot '"
                            + snapshot.semanticContractVersion().value() + "' != pinned '"
                            + expectedReference.semanticContractVersion().value() + "'");
        }
        // 4. tamper detection: recompute canonical digest over content (BI3).
        EffectSemanticSnapshotCanonicalSemantics.verifySnapshotDigest(snapshot);
        // 5. structural integrity: definition digests + V1 automation rule.
        for (EffectSemanticEntry entry : snapshot.entries()) {
            EffectDefinitionCanonicalSemantics.verifyDefinitionDigest(entry.definitionSnapshot());
        }

        return VerifiedEffectSemanticSnapshot.create(snapshot, expectedReference, revisionId);
    }
}

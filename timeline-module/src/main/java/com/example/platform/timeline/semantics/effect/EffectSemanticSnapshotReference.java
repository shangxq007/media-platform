package com.example.platform.timeline.semantics.effect;

import java.util.Objects;

/**
 * ROADMAP20 final implementation: the exact immutable pin a Timeline revision
 * holds over its Effect semantic state — EFFECT_SNAPSHOT_BINDING_IDENTITY_IS_DISTINCT_FROM_SEMANTIC_COMMITMENT_V1.
 *
 * <p>All three fields serve DIFFERENT roles:
 * <ul>
 *   <li>{@code snapshotId} — object/storage/binding identity (locate + verify
 *       the pinned snapshot; NOT semantic content; excluded from canonical
 *       semantic digests),</li>
 *   <li>{@code contentDigest} — semantic content commitment (participates in
 *       Effect semantic equality AND Timeline revision semantic digest),</li>
 *   <li>{@code semanticContractVersion} — semantic interpretation version
 *       (participates in Timeline revision semantic commitment).</li>
 * </ul>
 *
 * <p>Historical binding is IMMUTABLE: R1:S1 cannot silently become R1:S2 even
 * when digests match (HISTORICAL_EFFECT_SNAPSHOT_BINDING_IS_IMMUTABLE_V1);
 * semantic equality does not authorize historical reference mutation.
 */
public record EffectSemanticSnapshotReference(
        EffectSemanticSnapshotId snapshotId,
        String contentDigest,
        EffectSemanticContractVersion semanticContractVersion) {

    public EffectSemanticSnapshotReference {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(contentDigest, "contentDigest");
        if (contentDigest.isBlank()) {
            throw new IllegalArgumentException("contentDigest must not be blank");
        }
        Objects.requireNonNull(semanticContractVersion, "semanticContractVersion");
    }

    /** Semantic commitment text: digest + contract version (snapshot id EXCLUDED). */
    public String semanticCommitmentCanonical() {
        return "effectContractVersion=" + semanticContractVersion.value()
                + "\neffectContentDigest=" + contentDigest + "\n";
    }
}

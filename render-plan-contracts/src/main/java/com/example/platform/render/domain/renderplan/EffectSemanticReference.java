package com.example.platform.render.domain.renderplan;

import com.example.platform.timeline.semantics.effect.EffectSemanticContractVersion;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import java.util.Objects;

/**
 * ROADMAP20 final implementation: authoritative authored Effect semantic
 * reference carried by the FINAL Logical RenderPlan — the typed pin over the
 * immutable {@link EffectSemanticSnapshot} owned by the Timeline revision.
 *
 * <p>R4-A2/A3/A4 preserved: the final {@link RenderPlan} retains this
 * reference, it participates in the canonical RenderPlan fingerprint
 * ({@link #semanticContractVersion()} + {@link #effectStateDigest()}), and it
 * is explained in plan provenance.
 *
 * <p>Identity separation (bf7a2702): the reference carries
 * snapshotId + contentDigest + contractVersion for BINDING integrity, but only
 * contentDigest + contractVersion participate in canonical SEMANTIC digests —
 * snapshotId is an immutable handle, never semantic content
 * (EFFECT_SNAPSHOT_BINDING_IDENTITY_IS_DISTINCT_FROM_SEMANTIC_COMMITMENT_V1).
 */
public record EffectSemanticReference(EffectSemanticSnapshotReference reference, String revisionId) {

    public EffectSemanticReference {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(revisionId, "revisionId");
    }

    /** The authored revision this Effect semantic state is bound to. */
    public String revisionId() {
        return revisionId;
    }

    /** Immutable snapshot object/binding handle. */
    public EffectSemanticSnapshotId snapshotId() {
        return reference.snapshotId();
    }

    /** Semantic content commitment (participates in canonical digests). */
    public String effectStateDigest() {
        return reference.contentDigest();
    }

    /** Semantic interpretation version (participates in canonical commitment). */
    public EffectSemanticContractVersion semanticContractVersion() {
        return reference.semanticContractVersion();
    }

    /** The exact pinned reference. */
    public EffectSemanticSnapshotReference reference() {
        return reference;
    }
}

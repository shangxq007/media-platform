package com.example.platform.timeline.semantics.effect;

import java.util.List;
import java.util.Objects;

/**
 * ROADMAP20 final implementation: the immutable typed Effect semantic
 * authority (Option B) — EFFECT_SEMANTIC_SNAPSHOT_IS_PRIMARY_TYPED_AUTHORITY_WIRE_EFFECT_IS_DERIVED_PROJECTION_V1.
 *
 * <p>Contents:
 * <ul>
 *   <li>{@link EffectSemanticSnapshotId} — immutable object/binding handle
 *       (NOT semantic content; excluded from content digest),</li>
 *   <li>{@link EffectSemanticContractVersion} — semantic interpretation version,</li>
 *   <li>ordered target-bound effect stacks (per-target order IS semantic,
 *       EFFECT_STACK_ORDER_IS_AUTHORED_ORDERED_SEMANTICS_V1),</li>
 *   <li>exact embedded {@link EffectDefinitionSnapshot} semantics (D1),</li>
 *   <li>{@code contentDigest} — deterministic semantic content commitment
 *       (snapshot id excluded).</li>
 * </ul>
 *
 * <p>Construction is RESTRICTED: the constructor is package-private; the ONLY
 * minting path is {@link EffectSemanticSnapshotAuthority}
 * (EFFECT_SNAPSHOT_MINTING_IS_DOMAIN_AUTHORITY_ONLY_V1). Callers cannot claim
 * authoritative snapshot identity/digest/content.
 *
 * <p>Snapshot is IMMUTABLE: once minted, id → content is fixed
 * (BI4 — reusing an id with different content FAILS CLOSED at the store level;
 * the digest is value-bound here).
 */
public final class EffectSemanticSnapshot {

    private final EffectSemanticSnapshotId id;
    private final EffectSemanticContractVersion semanticContractVersion;
    private final List<EffectSemanticEntry> entries;
    private final String contentDigest;

    EffectSemanticSnapshot(
            EffectSemanticSnapshotId id,
            EffectSemanticContractVersion semanticContractVersion,
            List<EffectSemanticEntry> entries,
            String contentDigest) {
        this.id = Objects.requireNonNull(id, "id");
        this.semanticContractVersion = Objects.requireNonNull(semanticContractVersion, "semanticContractVersion");
        this.entries = List.copyOf(entries);
        Objects.requireNonNull(contentDigest, "contentDigest");
        if (contentDigest.isBlank()) {
            throw new IllegalArgumentException("contentDigest must not be blank");
        }
        this.contentDigest = contentDigest;
    }

    public EffectSemanticSnapshotId id() {
        return id;
    }

    public EffectSemanticContractVersion semanticContractVersion() {
        return semanticContractVersion;
    }

    /** Ordered target-bound effect stacks (list order IS semantic). */
    public List<EffectSemanticEntry> entries() {
        return entries;
    }

    /**
     * Semantic content commitment — computed over canonical semantic content
     * ONLY (snapshot id excluded). Two snapshots with identical semantic
     * content share this digest regardless of id (BI1).
     */
    public String contentDigest() {
        return contentDigest;
    }

    public EffectSemanticSnapshotReference reference() {
        return new EffectSemanticSnapshotReference(id, contentDigest, semanticContractVersion);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EffectSemanticSnapshot other
                && id.equals(other.id)
                && semanticContractVersion.equals(other.semanticContractVersion)
                && entries.equals(other.entries)
                && contentDigest.equals(other.contentDigest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, semanticContractVersion, entries, contentDigest);
    }

    @Override
    public String toString() {
        return "EffectSemanticSnapshot(" + id.value() + ", " + semanticContractVersion.value() + ")";
    }
}

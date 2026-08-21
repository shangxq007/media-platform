package com.example.platform.timeline.semantics.effect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * ROADMAP20 final implementation: immutable Effect snapshot persistence
 * contract (storage direction free per contract closure §27 — embed in
 * revision payload, dedicated row, or content-addressed store). This interface
 * declares the invariants; {@link InMemory} is the bounded implementation used
 * by the domain and tests today.
 *
 * <p>Invariants (frozen):
 * <ul>
 *   <li>immutable: once stored, id → content is fixed (BI4); re-store with
 *       different content FAILS CLOSED;</li>
 *   <li>same semantic content MAY be reused or persisted under another id;</li>
 *   <li>ownership-scoped exact lookup
 *       (EFFECT_SEMANTIC_SNAPSHOT_HAS_EXPLICIT_OWNERSHIP_V1): a snapshot is
 *       bound to (projectId, tenantId) and CANNOT be resolved by globally
 *       guessable id alone (CROSS_PROJECT/CROSS_TENANT authority forbidden);</li>
 *   <li>digest-verifiable (recomputed at store).</li>
 * </ul>
 */
public interface EffectSemanticSnapshotStore {

    /**
     * Persists an immutable snapshot WITHOUT ownership scope. InMemory stores
     * (pure domain tests) accept this; durable stores MUST fail closed and
     * require {@link #storeTx} with explicit ownership.
     */
    void store(EffectSemanticSnapshot snapshot);

    /**
     * Transaction-aware persist with EXPLICIT ownership (ROADMAP20 authority
     * integration + B4): joins the SAME physical transaction as the canonical
     * revision write and binds the row to (projectId, tenantId). Durable
     * stores write tenant_id — never null.
     */
    default void storeTx(org.jooq.DSLContext tx, String projectId, String tenantId,
                         EffectSemanticSnapshot snapshot) {
        store(snapshot);
    }

    /**
     * Ownership-scoped exact historical lookup
     * (AUTHORITATIVE_SNAPSHOT_LOOKUP_IS_OWNERSHIP_SCOPED_V1): the snapshot is
     * resolved only if it belongs to (projectId, tenantId). A globally
     * guessable id WITHOUT matching ownership resolves to empty / FAIL CLOSED.
     */
    Optional<EffectSemanticSnapshot> findById(String projectId, String tenantId,
                                              EffectSemanticSnapshotId id);

    /** In-memory bounded implementation for domain tests (NOT durable, ownership-tolerant). */
    final class InMemory implements EffectSemanticSnapshotStore {
        private final java.util.Map<String, EffectSemanticSnapshot> byId = new LinkedHashMap<>();

        @Override
        public synchronized void store(EffectSemanticSnapshot snapshot) {
            Objects.requireNonNull(snapshot, "snapshot");
            EffectSemanticSnapshot existing = byId.get(snapshot.id().value());
            if (existing != null && !existing.contentDigest().equals(snapshot.contentDigest())) {
                throw new IllegalArgumentException(
                        "EFFECT_SEMANTIC_SNAPSHOT_IMMUTABLE (BI4): snapshot id '"
                                + snapshot.id().value() + "' already stored with a different content digest");
            }
            byId.put(snapshot.id().value(), snapshot);
        }

        @Override
        public synchronized Optional<EffectSemanticSnapshot> findById(
                String projectId, String tenantId, EffectSemanticSnapshotId id) {
            Objects.requireNonNull(id, "id");
            return Optional.ofNullable(byId.get(id.value()));
        }
    }
}

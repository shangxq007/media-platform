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
 *   <li>exact historical lookup by id;</li>
 *   <li>digest-verifiable (recomputed at store).</li>
 * </ul>
 */
public interface EffectSemanticSnapshotStore {

    /** Persists an immutable snapshot. Re-store with different content fails closed. */
    void store(EffectSemanticSnapshot snapshot);

    /** Exact historical lookup by snapshot id. */
    Optional<EffectSemanticSnapshot> findById(EffectSemanticSnapshotId id);

    /**
     * In-memory bounded implementation (domain/tests). Not a second revision
     * DAG — a flat immutable semantic asset store.
     */
    final class InMemory implements EffectSemanticSnapshotStore {
        private final LinkedHashMap<String, EffectSemanticSnapshot> byId = new LinkedHashMap<>();

        @Override
        public synchronized void store(EffectSemanticSnapshot snapshot) {
            Objects.requireNonNull(snapshot, "snapshot");
            EffectSemanticSnapshot existing = byId.get(snapshot.id().value());
            if (existing != null) {
                if (!existing.contentDigest().equals(snapshot.contentDigest())) {
                    throw new IllegalArgumentException(
                            "SNAPSHOT IMMUTABILITY (BI4): snapshot id '"
                                    + snapshot.id().value() + "' already stored with a different "
                                    + "content digest — reusing an id with different semantic content "
                                    + "FAILS CLOSED");
                }
                return; // idempotent for identical content
            }
            EffectSemanticSnapshotCanonicalSemantics.verifySnapshotDigest(snapshot);
            byId.put(snapshot.id().value(), snapshot);
        }

        @Override
        public synchronized Optional<EffectSemanticSnapshot> findById(EffectSemanticSnapshotId id) {
            return Optional.ofNullable(byId.get(id.value()));
        }

        public synchronized List<EffectSemanticSnapshot> all() {
            return List.copyOf(byId.values());
        }
    }
}

package com.example.platform.timeline.semantics.effect;

import java.util.Objects;

/**
 * ROADMAP20 final implementation: immutable Effect semantic snapshot object /
 * binding handle. This is object/storage identity ONLY — it does NOT represent
 * semantic content equality and MUST NOT participate in any canonical semantic
 * digest (EFFECT_SNAPSHOT_HANDLE_DOES_NOT_PARTICIPATE_IN_CANONICAL_SEMANTIC_DIGEST_V1).
 *
 * <p>Two snapshots with different ids but identical semantic content share
 * their content digest and are semantically equal (SEMANTIC_EQUALITY_DOES_NOT_IMPLY_BINDING_IDENTITY_V1).
 */
public final class EffectSemanticSnapshotId {

    private final String value;

    private EffectSemanticSnapshotId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("snapshot id must not be blank");
        }
        this.value = value;
    }

    /** Creates a snapshot id from a stable external handle (e.g. storage key). */
    public static EffectSemanticSnapshotId of(String value) {
        return new EffectSemanticSnapshotId(value);
    }

    /** Creates a fresh id — used ONLY by the domain snapshot authority at mint time. */
    public static EffectSemanticSnapshotId generate() {
        return new EffectSemanticSnapshotId("esnap_" + java.util.UUID.randomUUID().toString().replace("-", ""));
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EffectSemanticSnapshotId other && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}

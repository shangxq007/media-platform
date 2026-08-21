package com.example.platform.timeline.semantics.effect;

import java.util.Objects;

/**
 * ROADMAP20 final implementation: Effect semantic contract version
 * (effect-semantics-v1). Participates in snapshot/reference verification and
 * Timeline revision semantic commitment. Future effect-semantics-v2 must
 * coexist with v1 historical snapshots; never normalize v1 into v2 in place
 * (CANONICAL_SCHEMA_EVOLUTION_V1).
 */
public final class EffectSemanticContractVersion {

    public static final String CURRENT_VERSION = "effect-semantics-v1";
    public static final EffectSemanticContractVersion V1 =
            new EffectSemanticContractVersion(CURRENT_VERSION);

    private final String value;

    private EffectSemanticContractVersion(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("semantic contract version must not be blank");
        }
        this.value = value;
    }

    public static EffectSemanticContractVersion of(String value) {
        return new EffectSemanticContractVersion(value);
    }

    public static EffectSemanticContractVersion current() {
        return V1;
    }

    /** B4/SC5: the single supported semantic contract (clean-forward V1-only). */
    public static boolean isSupported(String value) {
        return V1.value().equals(value);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EffectSemanticContractVersion other && value.equals(other.value);
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

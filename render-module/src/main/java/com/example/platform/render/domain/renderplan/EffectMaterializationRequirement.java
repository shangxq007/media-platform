package com.example.platform.render.domain.renderplan;

import java.util.List;
import java.util.Objects;

/**
 * ROADMAP20 correction F1: typed logical materialization requirement for an
 * EFFECT node.
 *
 * <p>Retains the semantically relevant materialized WHAT for an effect:
 * authoritative category (resolved from {@code EffectDefinition}) and the
 * supported effect parameters as a typed list of {@link EffectParameter}
 * key/value pairs. Immutable, deterministic, provider-neutral.
 * No provider filter names/commands; no opaque hash-only semantics; no
 * {@code Map<String,Object>} escape hatch.
 *
 * <p>Derived projection: category and parameters are authored semantics
 * consumed (never invented) by the render materializer.
 */
public record EffectMaterializationRequirement(
        com.example.platform.timeline.semantics.effect.EffectInstance.EffectCategory category,
        List<EffectParameter> parameters) implements RenderMaterializationRequirement {

    public EffectMaterializationRequirement {
        Objects.requireNonNull(category, "category");
        parameters = parameters != null ? List.copyOf(parameters) : List.of();
    }

    /**
     * Bounded typed effect parameter (derived projection of the authored
     * parameter map). Deterministic ordering is by {@link #key()} via
     * {@link EffectParameter#compareTo}.
     */
    public record EffectParameter(String key, String value) implements Comparable<EffectParameter> {

        public EffectParameter {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            if (key.isBlank()) {
                throw new IllegalArgumentException("parameter key must not be blank");
            }
        }

        @Override
        public int compareTo(EffectParameter other) {
            return this.key.compareTo(other.key);
        }
    }

    public static EffectMaterializationRequirement of(
            com.example.platform.timeline.semantics.effect.EffectInstance.EffectCategory category,
            List<EffectParameter> parameters) {
        return new EffectMaterializationRequirement(category, parameters);
    }

    /** Convenience factory from authored parameter entries (deterministic sorted). */
    public static EffectMaterializationRequirement ofSorted(
            com.example.platform.timeline.semantics.effect.EffectInstance.EffectCategory category,
            java.util.Collection<EffectParameter> parameters) {
        List<EffectParameter> sorted = parameters.stream().sorted().toList();
        return new EffectMaterializationRequirement(category, sorted);
    }

    /** Deterministic sorted parameter list for canonical encoding. */
    public List<EffectParameter> sortedParameters() {
        return parameters.stream().sorted().toList();
    }

    @Override
    public String variantKey() {
        return "EFFECT";
    }
}

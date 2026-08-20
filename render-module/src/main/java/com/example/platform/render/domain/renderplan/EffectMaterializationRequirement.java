package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.effect.ClipEffectTarget;
import com.example.platform.timeline.semantics.effect.EffectTarget;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import java.util.List;
import java.util.Objects;

/**
 * ROADMAP20 correction F1 + R5-B: typed logical materialization requirement for
 * an EFFECT node.
 *
 * <p>Retains the COMPLETE downstream-relevant authored Effect WHAT as a typed,
 * provider-neutral, immutable projection — a future physical planner can
 * materialize the effect WITHOUT re-reading {@code EffectInstance} /
 * {@code EffectDefinition} / mutable authored repository state:
 * <ul>
 *   <li>{@code effectInstanceId} — authoritative effect instance identity,</li>
 *   <li>{@code effectDefinitionId} + {@code effectDefinitionVersion} —
 *       authoritative definition identity AND version (def-blur@1 vs
 *       def-blur@2 distinguishable even with identical category/parameters),</li>
 *   <li>{@code category} — authority-resolved category (from the definition),</li>
 *   <li>{@code enabled} — disabled effects use OPTION A semantics (no execution
 *       node; retained in the verified authored snapshot + plan reference),</li>
 *   <li>{@code applicationRange} — typed exact half-open rational range
 *       ([0,1) vs [0,2) distinguishable),</li>
 *   <li>{@code parameters} — typed key/value pairs,</li>
 *   <li>{@code automationBindings} — typed key→reference pairs (authoritative
 *       automation reference recoverable, not hash-only),</li>
 *   <li>{@code temporalBehavior} — typed effect temporal semantics, fail-closed
 *       on unsupported variants at materialization.</li>
 * </ul>
 *
 * <p>Derived projection: all fields are authored semantics consumed (never
 * invented) by the render materializer; the single Effect domain authority
 * owns the semantic field participation (provenance fields excluded).
 * No provider filter names/commands; no opaque hash-only semantics; no
 * {@code Map<String,Object>} escape hatch.
 */
public record EffectMaterializationRequirement(
        EffectInstance.EffectCategory category,
        List<EffectParameter> parameters,
        String effectInstanceId,
        String effectDefinitionId,
        String effectDefinitionVersion,
        boolean enabled,
        MediaClip.TimeRange applicationRange,
        List<AutomationBinding> automationBindings,
        EffectInstance.EffectTemporalBehavior temporalBehavior,
        EffectTarget target)
        implements RenderMaterializationRequirement {

    public EffectMaterializationRequirement {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(effectInstanceId, "effectInstanceId");
        Objects.requireNonNull(effectDefinitionId, "effectDefinitionId");
        Objects.requireNonNull(effectDefinitionVersion, "effectDefinitionVersion");
        Objects.requireNonNull(applicationRange, "applicationRange");
        Objects.requireNonNull(temporalBehavior, "temporalBehavior");
        Objects.requireNonNull(target, "target"); // R6-A: typed authored target mandatory
        if (effectInstanceId.isBlank()) {
            throw new IllegalArgumentException("effectInstanceId must not be blank");
        }
        if (effectDefinitionId.isBlank()) {
            throw new IllegalArgumentException("effectDefinitionId must not be blank");
        }
        if (effectDefinitionVersion.isBlank()) {
            throw new IllegalArgumentException("effectDefinitionVersion must not be blank");
        }
        parameters = parameters != null ? List.copyOf(parameters) : List.of();
        automationBindings = automationBindings != null ? List.copyOf(automationBindings) : List.of();
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

    /**
     * R5-B: typed authoritative automation binding reference — the binding key
     * (the automation parameter path) and the authoritative automation
     * reference string. The reference is the typed/ref form a downstream layer
     * uses to resolve canonical automation semantics; it is recoverable from
     * the Logical Plan, not hash-only.
     */
    public record AutomationBinding(String parameterKey, String automationReference)
            implements Comparable<AutomationBinding> {

        public AutomationBinding {
            Objects.requireNonNull(parameterKey, "parameterKey");
            Objects.requireNonNull(automationReference, "automationReference");
            if (parameterKey.isBlank()) {
                throw new IllegalArgumentException("automation parameterKey must not be blank");
            }
        }

        @Override
        public int compareTo(AutomationBinding other) {
            return this.parameterKey.compareTo(other.parameterKey);
        }
    }

    /**
     * R6-D: the ONLY public factory — complete typed Logical Effect WHAT,
     * resolved from the authored effect instance + authoritative definition
     * (which carries the typed authored target). No partial/incomplete
     * construction path exists.
     */
    public static EffectMaterializationRequirement ofComplete(
            EffectInstance effect,
            EffectInstance.EffectDefinition definition,
            List<EffectParameter> parameters,
            List<AutomationBinding> automationBindings) {
        Objects.requireNonNull(effect, "effect");
        if (effect.target() == null) {
            throw new IllegalArgumentException(
                    "EffectInstance " + effect.effectInstanceId()
                            + " carries no authored EffectTarget (R6-D fail closed)");
        }
        return new EffectMaterializationRequirement(
                definition.category(),
                parameters,
                effect.effectInstanceId(),
                definition.definitionId(),
                definition.version(),
                effect.enabled(),
                effect.applicationRange(),
                automationBindings,
                definition.temporalBehavior(),
                effect.target());
    }

    /** Deterministic sorted parameter list for canonical encoding. */
    public List<EffectParameter> sortedParameters() {
        return parameters.stream().sorted().toList();
    }

    /** Deterministic sorted automation binding list for canonical encoding. */
    public List<AutomationBinding> sortedAutomationBindings() {
        return automationBindings.stream().sorted().toList();
    }

    @Override
    public String variantKey() {
        return "EFFECT";
    }
}

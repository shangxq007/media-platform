package com.example.platform.timeline.semantics.effect;

import java.util.List;
import java.util.Objects;

/**
 * ROADMAP20 final implementation: authoritative typed Effect semantic entry
 * inside an {@link EffectSemanticSnapshot}.
 *
 * <p>Authored/authority fields: effectInstanceId, target, exact
 * {@link EffectDefinitionSnapshot} (D1 embed), enabled, parameters,
 * automationBindings.
 *
 * <p>Derived fields (applicationRange, mediaType) are NOT independent
 * caller-controlled authority:
 * <ul>
 *   <li>applicationRange = DERIVED from target clip extent
 *       (APPLICATION_RANGE_AUTHORITY_V1);</li>
 *   <li>mediaType = DERIVED from target track kind ∩ definition
 *       supportedMediaTypes (EFFECT_MEDIA_TYPE_IS_DERIVED_V1).</li>
 * </ul>
 * V1 automation rule: automationBindings MUST be empty
 * (UNVERIFIED_EFFECT_AUTOMATION_REFERENCES_FAIL_CLOSED_V1).
 */
public record EffectSemanticEntry(
        String effectInstanceId,
        EffectTarget target,
        EffectDefinitionSnapshot definitionSnapshot,
        boolean enabled,
        List<EffectParameter> parameters,
        List<EffectAutomationBinding> automationBindings) {

    public EffectSemanticEntry {
        Objects.requireNonNull(effectInstanceId, "effectInstanceId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(definitionSnapshot, "definitionSnapshot");
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
        automationBindings = List.copyOf(automationBindings == null ? List.of() : automationBindings);
        // V1 automation rule: non-empty unverified automation FAILS CLOSED.
        if (!automationBindings.isEmpty()) {
            throw new IllegalArgumentException(
                    "UNVERIFIED_EFFECT_AUTOMATION_REFERENCES_FAIL_CLOSED_V1: non-empty "
                            + "effect automationBindings are unsupported in effect-semantics-v1");
        }
    }

    /** Typed parameter pair (same shared pair semantics as R4-B). */
    public record EffectParameter(String key, String value) {
        public EffectParameter {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * Typed automation binding slot. V1: only EMPTY lists are accepted at
     * construction; this record exists so the schema shape is explicit and a
     * future typed immutable AutomationSemanticReference can fill it without a
     * contract change.
     */
    public record EffectAutomationBinding(String parameterPath, String reference) {
        public EffectAutomationBinding {
            Objects.requireNonNull(parameterPath, "parameterPath");
            Objects.requireNonNull(reference, "reference");
        }
    }
}

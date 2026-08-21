package com.example.platform.timeline.semantics.effect;

import java.util.List;
import java.util.Objects;

/**
 * ROADMAP20 final implementation: D1 — exact immutable EffectDefinition
 * semantic snapshot EMBEDDED in the EffectSemanticSnapshot. Contains every
 * canonical semantic field of {@code EffectInstance.EffectDefinition} that
 * participates in typed/render WHAT, plus a deterministic
 * {@code definitionContentDigest}.
 *
 * <p>{@code supportedBackendCapabilities} is deliberately EXCLUDED: it is
 * EXECUTION/PROVIDER METADATA, not authored Effect meaning
 * (EFFECT_DEFINITION_SEMANTICS_ARE_EXACTLY_PINNED_IN_EFFECT_SNAPSHOT_V1).
 *
 * <p>EFFECT_DEFINITION_VERSION_CONTENT_IS_IMMUTABLE_V1: the same
 * (definitionId, version) MUST map to exactly one content digest across all
 * authoritative snapshots; a collision with different content FAILS CLOSED.
 */
public record EffectDefinitionSnapshot(
        String definitionId,
        String version,
        String category,
        List<String> supportedMediaTypes,
        List<EffectParameterSchemaEntry> parameterSchema,
        String temporalBehavior,
        List<String> deterministicProperties,
        List<String> requiredCapabilities,
        String definitionContentDigest) {

    public EffectDefinitionSnapshot {
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(category, "category");
        supportedMediaTypes = List.copyOf(supportedMediaTypes == null ? List.of() : supportedMediaTypes);
        parameterSchema = List.copyOf(parameterSchema == null ? List.of() : parameterSchema);
        deterministicProperties = List.copyOf(deterministicProperties == null ? List.of() : deterministicProperties);
        requiredCapabilities = List.copyOf(requiredCapabilities == null ? List.of() : requiredCapabilities);
        Objects.requireNonNull(definitionContentDigest, "definitionContentDigest");
    }

    /**
     * Typed parameter schema entry: parameter name + declared type marker
     * (bounded V1 schema vocabulary — no generic schema language).
     */
    public record EffectParameterSchemaEntry(String name, String type, String defaultValue) {
        public EffectParameterSchemaEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
        }
    }
}

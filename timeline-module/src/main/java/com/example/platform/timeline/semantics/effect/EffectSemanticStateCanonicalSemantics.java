package com.example.platform.timeline.semantics.effect;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.TreeMap;

/**
 * ROADMAP20 correction R4-C: THE single authoritative canonical encoder for
 * authored {@link EffectInstance}/{@link EffectDefinition} semantic state.
 *
 * <p>Timeline / Effect domain OWNS authored Effect semantics; render consumes
 * this authority and MUST NOT define a second Effect domain canonical grammar.
 * The wire-layer {@code TimelineClipEffect} authority
 * ({@code timeline.canonicalmodel.EffectCanonicalSemantics}) remains the
 * canonical authority for the persisted {@code clip.effects[]} projection; this
 * class is the semantic-layer authority for the typed
 * {@code EffectInstance}/{@code EffectDefinition} model consumed by planning.
 * They are different layers, deliberately not merged (R4-C2: ONE SEMANTIC
 * AUTHORITY, not necessarily ONE JAVA TYPE).
 *
 * <p>SEMANTIC vs PROVENANCE field classification (R4-A5): the following
 * EffectInstance fields are authored semantic WHAT and participate in the
 * canonical encoding:
 * <ul>
 *   <li>effectInstanceId</li>
 *   <li>effectDefinitionId</li>
 *   <li>effectDefinitionVersion</li>
 *   <li>mediaType</li>
 *   <li>enabled</li>
 *   <li>applicationRange (start/end)</li>
 *   <li>parameters (deep-sorted, unordered semantic collection)</li>
 *   <li>automationBindings (deep-sorted, unordered semantic collection)</li>
 * </ul>
 * {@code provenance} (source/sourceId/createdAt) is EXPLANATORY provenance —
 * it is excluded from the canonical semantic encoding.
 *
 * <p>Framing rules (R4-B): every field is independently length-prefixed
 * ({@code len:value}); parameter pairs use {@link #encodeParameterPair(String, String)}
 * which frames key and value SEPARATELY — never {@code key + ":" + value}
 * delimiter flattening. Collection sections are count-framed. Semantic-equal
 * state (reconstructed from fresh objects) yields identical bytes; distinct
 * state yields distinct bytes; no delimiter-based collision is possible.
 */
public final class EffectSemanticStateCanonicalSemantics {

    private EffectSemanticStateCanonicalSemantics() {
    }

    /**
     * The ONE canonical encoding of an {@code EffectInstance} semantic value.
     * Provenance fields excluded (R4-A5). Ordered, length-prefixed fields.
     */
    public static String canonicalEffectInstance(EffectInstance effect) {
        StringBuilder sb = new StringBuilder();
        field(sb, "instanceId", effect.effectInstanceId());
        field(sb, "definitionId", effect.effectDefinitionId());
        field(sb, "definitionVersion", effect.effectDefinitionVersion());
        field(sb, "mediaType", effect.mediaType().name());
        field(sb, "enabled", Boolean.toString(effect.enabled()));
        field(sb, "rangeStart", effect.applicationRange().start().toString());
        field(sb, "rangeEnd", effect.applicationRange().end().toString());
        // parameters: unordered semantic collection -> deep-sorted by key
        field(sb, "parameterCount", Integer.toString(effect.parameters().size()));
        for (java.util.Map.Entry<String, String> entry : new TreeMap<>(effect.parameters()).entrySet()) {
            field(sb, "parameterKey", entry.getKey());
            field(sb, "parameterValue", entry.getValue());
        }
        // automation bindings: unordered semantic collection -> deep-sorted by key
        field(sb, "automationCount", Integer.toString(effect.automationBindings().size()));
        for (java.util.Map.Entry<String, String> entry : new TreeMap<>(effect.automationBindings()).entrySet()) {
            field(sb, "automationKey", entry.getKey());
            field(sb, "automationValue", entry.getValue());
        }
        return sb.toString();
    }

    /** Canonical encoding of an {@code EffectDefinition} semantic value. */
    public static String canonicalEffectDefinition(EffectInstance.EffectDefinition definition) {
        StringBuilder sb = new StringBuilder();
        field(sb, "definitionId", definition.definitionId());
        field(sb, "version", definition.version());
        field(sb, "category", definition.category().name());
        field(sb, "temporalBehavior", definition.temporalBehavior().name());
        field(sb, "supportedMediaCount", Integer.toString(definition.supportedMediaTypes().size()));
        for (EffectInstance.EffectMediaType mediaType : definition.supportedMediaTypes()) {
            field(sb, "mediaType", mediaType.name());
        }
        field(sb, "parameterSchemaCount", Integer.toString(definition.parameterSchema().size()));
        for (java.util.Map.Entry<String, EffectInstance.ParameterSchema> entry
                : new TreeMap<>(definition.parameterSchema()).entrySet()) {
            field(sb, "schemaKey", entry.getKey());
            EffectInstance.ParameterSchema schema = entry.getValue();
            field(sb, "schemaPath", schema.path());
            field(sb, "schemaValueType", schema.valueType());
            field(sb, "schemaMin", schema.minValue() != null ? schema.minValue().toString() : "");
            field(sb, "schemaMax", schema.maxValue() != null ? schema.maxValue().toString() : "");
            field(sb, "schemaDefault", schema.defaultValue() != null ? schema.defaultValue() : "");
            field(sb, "schemaEnumCount", Integer.toString(schema.enumValues().size()));
            for (String enumValue : schema.enumValues()) {
                field(sb, "schemaEnumValue", enumValue);
            }
        }
        field(sb, "deterministicCount", Integer.toString(definition.deterministicProperties().size()));
        for (String property : definition.deterministicProperties()) {
            field(sb, "deterministicProperty", property);
        }
        field(sb, "requiredCapabilityCount", Integer.toString(definition.requiredCapabilities().size()));
        for (String capability : definition.requiredCapabilities()) {
            field(sb, "requiredCapability", capability);
        }
        return sb.toString();
    }

    /**
     * The ONE canonical encoding of the complete authored Effect semantic
     * state: instances sorted by instance id, definitions sorted by definition
     * id, sections count-framed (R3-B2/R4-B framing rules).
     */
    public static String canonicalEffectState(
            List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> effectDefinitions) {
        StringBuilder sb = new StringBuilder();
        List<EffectInstance> sortedEffects = effects.stream()
                .sorted(java.util.Comparator.comparing(EffectInstance::effectInstanceId))
                .toList();
        field(sb, "effects", Integer.toString(sortedEffects.size()));
        for (EffectInstance effect : sortedEffects) {
            field(sb, "effect", canonicalEffectInstance(effect));
        }
        List<EffectInstance.EffectDefinition> sortedDefinitions = effectDefinitions.stream()
                .sorted(java.util.Comparator.comparing(EffectInstance.EffectDefinition::definitionId))
                .toList();
        field(sb, "definitions", Integer.toString(sortedDefinitions.size()));
        for (EffectInstance.EffectDefinition definition : sortedDefinitions) {
            field(sb, "definition", canonicalEffectDefinition(definition));
        }
        return sb.toString();
    }

    /**
     * R4-B: THE single parameter pair framing used by every Effect canonical
     * path. Key and value are framed SEPARATELY (length-prefixed), so
     * {@code ("a:b","c")} and {@code ("a","b:c")} produce distinct bytes.
     * No delimiter-based flattening anywhere.
     */
    public static String encodeParameterPair(String key, String value) {
        StringBuilder sb = new StringBuilder();
        field(sb, "k", key);
        field(sb, "v", value);
        return sb.toString();
    }

    /** SHA-256 hex (lowercase) of the canonical state bytes. */
    public static String sha256Hex(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Length-prefixed field: unambiguous concatenation (no delimiter collisions). */
    private static void field(StringBuilder sb, String name, String value) {
        sb.append(name.length()).append(':').append(name)
                .append('=').append(value.length()).append(':').append(value)
                .append(';');
    }
}

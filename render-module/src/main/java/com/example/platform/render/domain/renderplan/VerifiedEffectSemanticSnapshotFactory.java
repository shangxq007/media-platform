package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * ROADMAP20 correction R3-B1: verification factory for the immutable authored
 * effect semantic snapshot.
 *
 * <p>This is the ONLY public construction path for
 * {@link VerifiedEffectSemanticSnapshot}. It performs REAL integrity checks —
 * not caller-trust documentation:
 * <ul>
 *   <li>every {@code EffectInstance.effectDefinitionId} must resolve to a
 *       definition in the supplied catalog (else FAIL CLOSED),</li>
 *   <li>every {@code EffectInstance.effectDefinitionVersion} must equal the
 *       referenced definition's {@code version} (else FAIL CLOSED),</li>
 *   <li>the content pin is a SHA-256 over an explicit deterministic encoding of
 *       the COMPLETE typed effect state — effect instances sorted by instance
 *       id, parameters deep-sorted (key-ordered), all authored semantic fields
 *       length-prefixed. Value-bound: semantic-equal reconstructed state yields
 *       the same pin; distinct state yields a distinct pin.</li>
 * </ul>
 *
 * <p>This pin is a render planning-boundary semantic pin (explicitly
 * introduced, documented, and guarded — NOT a fake digest over arbitrary caller
 * lists): it binds exactly the authored effect semantics the Logical RenderPlan
 * consumes, and it participates in the logical plan provenance.
 */
public final class VerifiedEffectSemanticSnapshotFactory {

    private VerifiedEffectSemanticSnapshotFactory() {
    }

    /**
     * Verifies and pins the authored effect semantic state.
     *
     * @param effects           typed effect instances (authored WHAT)
     * @param effectDefinitions effect definition catalog
     * @return immutable verified effect semantic snapshot
     * @throws IllegalArgumentException on unknown definition reference or
     *                                  version mismatch (fail closed)
     */
    public static VerifiedEffectSemanticSnapshot verified(
            List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> effectDefinitions) {
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(effectDefinitions, "effectDefinitions");

        // 1/2. definition reference + version integrity (fail closed).
        // Resolved by direct stream lookup — avoids any map token in the
        // renderplan package (C20 guard forbids Map<String literal).
        for (EffectInstance effect : effects) {
            EffectInstance.EffectDefinition definition = effectDefinitions.stream()
                    .filter(d -> d.definitionId().equals(effect.effectDefinitionId()))
                    .findFirst()
                    .orElse(null);
            if (definition == null) {
                throw new IllegalArgumentException(
                        "Effect " + effect.effectInstanceId() + " references unknown "
                                + "effectDefinitionId '" + effect.effectDefinitionId() + "'");
            }
            if (!definition.version().equals(effect.effectDefinitionVersion())) {
                throw new IllegalArgumentException(
                        "Effect " + effect.effectInstanceId() + " version mismatch: "
                                + "instance requests '" + effect.effectDefinitionVersion()
                                + "' but definition '" + effect.effectDefinitionId()
                                + "' is version '" + definition.version() + "'");
            }
        }

        // 3. deterministic value-bound content pin.
        ContentDigest pin = ContentDigest.sha256(encodeEffectState(effects, effectDefinitions));
        return VerifiedEffectSemanticSnapshot.create(effects, effectDefinitions, pin);
    }

    /**
     * Explicit deterministic encoding of the complete typed effect state.
     * Instance order is canonical (sorted by instance id); parameters are
     * deep-sorted; every authored semantic field is length-prefixed; sections
     * are explicitly framed (list count + element count) so no two distinct
     * semantic structures share canonical bytes (R3-B2 framing rule).
     */
    static String encodeEffectState(
            List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> effectDefinitions) {
        StringBuilder sb = new StringBuilder();
        List<EffectInstance> sortedEffects = effects.stream()
                .sorted(java.util.Comparator.comparing(EffectInstance::effectInstanceId))
                .toList();

        // ── effects section (count-framed) ──
        field(sb, "effects", Integer.toString(sortedEffects.size()));
        for (EffectInstance effect : sortedEffects) {
            field(sb, "instanceId", effect.effectInstanceId());
            field(sb, "definitionId", effect.effectDefinitionId());
            field(sb, "definitionVersion", effect.effectDefinitionVersion());
            field(sb, "mediaType", effect.mediaType().name());
            field(sb, "enabled", Boolean.toString(effect.enabled()));
            field(sb, "rangeStart", effect.applicationRange().start().toString());
            field(sb, "rangeEnd", effect.applicationRange().end().toString());
            // parameters: deep-sorted map entries (key-ordered)
            field(sb, "parameterCount", Integer.toString(effect.parameters().size()));
            for (java.util.Map.Entry<String, String> entry : new TreeMap<>(effect.parameters()).entrySet()) {
                field(sb, "parameterKey", entry.getKey());
                field(sb, "parameterValue", entry.getValue());
            }
            field(sb, "automationCount", Integer.toString(effect.automationBindings().size()));
            for (java.util.Map.Entry<String, String> entry : new TreeMap<>(effect.automationBindings()).entrySet()) {
                field(sb, "automationKey", entry.getKey());
                field(sb, "automationValue", entry.getValue());
            }
            field(sb, "provenanceSource", effect.provenance().source());
            field(sb, "provenanceSourceId", effect.provenance().sourceId());
            field(sb, "provenanceCreatedAt", Long.toString(effect.provenance().createdAt()));
        }

        // ── definitions section (count-framed, sorted by definition id) ──
        List<EffectInstance.EffectDefinition> sortedDefinitions = effectDefinitions.stream()
                .sorted(java.util.Comparator.comparing(EffectInstance.EffectDefinition::definitionId))
                .toList();
        field(sb, "definitions", Integer.toString(sortedDefinitions.size()));
        for (EffectInstance.EffectDefinition definition : sortedDefinitions) {
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
        }

        // SHA-256 of the canonical bytes
        return sha256Hex(sb.toString());
    }

    /** Length-prefixed field: unambiguous concatenation (R3-B2 framing). */
    private static void field(StringBuilder sb, String name, String value) {
        sb.append(name.length()).append(':').append(name)
                .append('=').append(value.length()).append(':').append(value)
                .append(';');
    }

    private static String sha256Hex(String bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

package com.example.platform.timeline.semantics.effect;

import com.example.platform.shared.digest.ContentDigest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ROADMAP20 final implementation: THE single canonical encoder for
 * {@link EffectDefinitionSnapshot} semantic content — the definition content
 * digest authority (EFFECT_DEFINITION_SEMANTICS_ARE_EXACTLY_PINNED_IN_EFFECT_SNAPSHOT_V1).
 *
 * <p>Deterministic rules:
 * <ul>
 *   <li>semantically unordered collections (supportedMediaTypes,
 *       deterministicProperties, requiredCapabilities, parameterSchema) are
 *       deep-sorted before encoding (same discipline as R5-F);</li>
 *   <li>no runtime class names, no Java serialization, no reflection order,
 *       no Map iteration order (list-of-pairs representation only);</li>
 *   <li>changing ANY canonical semantic field (category, supportedMediaTypes,
 *       parameterSchema, temporalBehavior, deterministicProperties,
 *       requiredCapabilities) changes the digest;</li>
 *   <li>supportedBackendCapabilities is NOT part of this encoding — provider
 *       metadata never affects canonical definition semantics.</li>
 * </ul>
 */
public final class EffectDefinitionCanonicalSemantics {

    private EffectDefinitionCanonicalSemantics() {
    }

    /** Canonical semantic text of a definition snapshot (digest input). */
    public static String canonicalDefinition(EffectDefinitionSnapshot definition) {
        StringBuilder sb = new StringBuilder();
        field(sb, "definitionId", definition.definitionId());
        field(sb, "version", definition.version());
        field(sb, "category", definition.category());
        sortedListField(sb, "supportedMediaTypes", definition.supportedMediaTypes());
        List<String> schemaCanonical = new ArrayList<>();
        for (EffectDefinitionSnapshot.EffectParameterSchemaEntry entry : definition.parameterSchema()) {
            schemaCanonical.add("(" + entry.name() + "|" + entry.type() + ")");
        }
        sortedListField(sb, "parameterSchema", schemaCanonical);
        field(sb, "temporalBehavior", definition.temporalBehavior());
        sortedListField(sb, "deterministicProperties", definition.deterministicProperties());
        sortedListField(sb, "requiredCapabilities", definition.requiredCapabilities());
        return sb.toString();
    }

    /** Deterministic SHA-256 hex of the canonical definition semantics. */
    public static String definitionContentDigest(EffectDefinitionSnapshot definition) {
        return sha256Hex(canonicalDefinition(definition));
    }

    /**
     * Validates that the snapshot's pinned {@code definitionContentDigest}
     * matches a fresh recomputation (BI3-style tamper detection; also used for
     * D1/D2-D4 change-detection). FAIL CLOSED on mismatch.
     */
    public static void verifyDefinitionDigest(EffectDefinitionSnapshot definition) {
        String recomputed = definitionContentDigest(definition);
        if (!recomputed.equals(definition.definitionContentDigest())) {
            throw new IllegalArgumentException(
                    "EffectDefinitionSnapshot content digest mismatch for "
                            + definition.definitionId() + "@" + definition.version()
                            + ": pinned '" + definition.definitionContentDigest()
                            + "' recomputed '" + recomputed + "'");
        }
    }

    public static String sha256Hex(String canonical) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void field(StringBuilder sb, String key, String value) {
        sb.append(key).append('=').append(value == null ? "" : value).append('\n');
    }

    private static void sortedListField(StringBuilder sb, String key, List<String> values) {
        sb.append(key).append('=');
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        sb.append('[');
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(sorted.get(i));
        }
        sb.append(']').append('\n');
    }
}

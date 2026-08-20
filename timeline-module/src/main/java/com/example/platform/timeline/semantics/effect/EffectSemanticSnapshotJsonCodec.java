package com.example.platform.timeline.semantics.effect;

import com.example.platform.timeline.app.InternalTimelineJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;

/**
 * ROADMAP20 final implementation: deterministic JSON serialization of
 * {@link EffectSemanticSnapshot} for durable persistence (snapshot payload) —
 * canonical serialized Effect document, NOT a second revision DAG.
 *
 * <p>On deserialize, the content digest is RECOMPUTED and verified against the
 * stored digest (BI3 tamper detection across restart; RP2).
 */
public final class EffectSemanticSnapshotJsonCodec {

    private EffectSemanticSnapshotJsonCodec() {
    }

    public static String serialize(EffectSemanticSnapshot snapshot) {
        ObjectNode root = InternalTimelineJson.mapper().createObjectNode();
        root.put("snapshotId", snapshot.id().value());
        root.put("semanticContractVersion", snapshot.semanticContractVersion().value());
        root.put("contentDigest", snapshot.contentDigest());
        ArrayNode entries = root.putArray("entries");
        for (EffectSemanticEntry entry : snapshot.entries()) {
            ObjectNode en = entries.addObject();
            en.put("effectInstanceId", entry.effectInstanceId());
            ObjectNode target = en.putObject("target");
            if (entry.target() instanceof ClipEffectTarget clip) {
                target.put("type", "clip");
                target.put("trackId", clip.trackId());
                target.put("clipId", clip.clipId());
            }
            en.put("enabled", entry.enabled());
            ObjectNode def = en.putObject("definition");
            EffectDefinitionSnapshot d = entry.definitionSnapshot();
            def.put("definitionId", d.definitionId());
            def.put("version", d.version());
            def.put("category", d.category());
            def.put("temporalBehavior", d.temporalBehavior());
            def.put("definitionContentDigest", d.definitionContentDigest());
            ArrayNode mediaTypes = def.putArray("supportedMediaTypes");
            d.supportedMediaTypes().forEach(mediaTypes::add);
            ArrayNode schema = def.putArray("parameterSchema");
            for (EffectDefinitionSnapshot.EffectParameterSchemaEntry e : d.parameterSchema()) {
                ObjectNode se = schema.addObject();
                se.put("name", e.name());
                se.put("type", e.type());
            }
            ArrayNode det = def.putArray("deterministicProperties");
            d.deterministicProperties().forEach(det::add);
            ArrayNode caps = def.putArray("requiredCapabilities");
            d.requiredCapabilities().forEach(caps::add);
            ArrayNode params = en.putArray("parameters");
            for (EffectSemanticEntry.EffectParameter p : entry.parameters()) {
                ObjectNode pn = params.addObject();
                pn.put("key", p.key());
                pn.put("value", p.value());
            }
            en.putArray("automationBindings"); // V1: always empty
        }
        try {
            return InternalTimelineJson.mapper().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize EffectSemanticSnapshot", e);
        }
    }

    /**
     * Deserializes and VERIFIES: recomputed digest must equal the stored
     * digest (BI3/RP2). The snapshot id is preserved as the binding handle.
     */
    public static EffectSemanticSnapshot deserialize(String payload) {
        try {
            JsonNode root = InternalTimelineJson.mapper().readTree(payload);
            EffectSemanticSnapshotId id = EffectSemanticSnapshotId.of(root.get("snapshotId").asText());
            EffectSemanticContractVersion version =
                    EffectSemanticContractVersion.of(root.get("semanticContractVersion").asText());
            String storedDigest = root.get("contentDigest").asText();
            List<EffectSemanticEntry> entries = new ArrayList<>();
            for (JsonNode en : root.get("entries")) {
                String instanceId = en.get("effectInstanceId").asText();
                JsonNode t = en.get("target");
                EffectTarget target = new ClipEffectTarget(t.get("trackId").asText(), t.get("clipId").asText());
                boolean enabled = en.get("enabled").asBoolean();
                JsonNode d = en.get("definition");
                List<String> mediaTypes = new ArrayList<>();
                d.get("supportedMediaTypes").forEach(n -> mediaTypes.add(n.asText()));
                List<EffectDefinitionSnapshot.EffectParameterSchemaEntry> schema = new ArrayList<>();
                d.get("parameterSchema").forEach(n -> schema.add(
                        new EffectDefinitionSnapshot.EffectParameterSchemaEntry(
                                n.get("name").asText(), n.get("type").asText())));
                List<String> det = new ArrayList<>();
                d.get("deterministicProperties").forEach(n -> det.add(n.asText()));
                List<String> caps = new ArrayList<>();
                d.get("requiredCapabilities").forEach(n -> caps.add(n.asText()));
                EffectDefinitionSnapshot defSnapshot = new EffectDefinitionSnapshot(
                        d.get("definitionId").asText(), d.get("version").asText(),
                        d.get("category").asText(), mediaTypes, schema,
                        d.get("temporalBehavior").asText(), det, caps,
                        d.get("definitionContentDigest").asText());
                List<EffectSemanticEntry.EffectParameter> params = new ArrayList<>();
                en.get("parameters").forEach(n -> params.add(
                        new EffectSemanticEntry.EffectParameter(n.get("key").asText(), n.get("value").asText())));
                entries.add(new EffectSemanticEntry(instanceId, target, defSnapshot, enabled, params, List.of()));
            }
            EffectSemanticSnapshot reconstructed = new EffectSemanticSnapshot(id, version, entries, storedDigest);
            EffectSemanticSnapshotCanonicalSemantics.verifySnapshotDigest(reconstructed);
            for (EffectSemanticEntry entry : entries) {
                EffectDefinitionCanonicalSemantics.verifyDefinitionDigest(entry.definitionSnapshot());
            }
            return reconstructed;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize EffectSemanticSnapshot (corrupt payload)", e);
        }
    }
}

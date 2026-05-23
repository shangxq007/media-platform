package com.example.platform.render.domain.timeline;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Reads v2 timeline fields from JSON root or {@code platform.*} metadata keys.
 */
@Component
public class TimelineExtensionsReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String META_FINAL_COMPOSER = "platform.finalComposer";
    private static final String META_EXTERNAL_NODES = "platform.externalRenderNodes";
    private static final String META_MARKERS = "platform.markers";
    private static final String META_TRANSITIONS = "platform.transitions";
    private static final String META_PACKAGING = "platform.packaging";
    private static final String META_OTIO_LOSSY = "platform.otio.exportLossy";

    public TimelineExtensions fromSpec(TimelineSpec spec) {
        if (spec == null) {
            return TimelineExtensions.defaults();
        }
        Map<String, String> meta = spec.metadata() != null ? spec.metadata() : Map.of();
        FinalComposerHint composer = FinalComposerHint.fromString(meta.get(META_FINAL_COMPOSER));
        return new TimelineExtensions(
                meta.getOrDefault("schemaVersion", "1.0"),
                composer,
                parseExternalNodes(meta.get(META_EXTERNAL_NODES)),
                parseMarkers(meta.get(META_MARKERS)),
                parseTransitions(meta.get(META_TRANSITIONS)),
                parsePackaging(meta.get(META_PACKAGING)),
                Boolean.parseBoolean(meta.getOrDefault(META_OTIO_LOSSY, "false")));
    }

    public TimelineExtensions fromJsonRoot(JsonNode root) {
        Map<String, String> meta = new LinkedHashMap<>();
        if (root.has("metadata") && root.get("metadata").isObject()) {
            root.get("metadata").fields().forEachRemaining(e ->
                    meta.put(e.getKey(), e.getValue().asText("")));
        }
        FinalComposerHint composer = resolveFinalComposer(root, meta);

        List<ExternalRenderNode> external = new ArrayList<>();
        JsonNode renderGraph = root.path("renderGraph");
        if (renderGraph.has("externalRenderNodes") && renderGraph.get("externalRenderNodes").isArray()) {
            int fps = frameRateFromRoot(root);
            for (JsonNode node : renderGraph.get("externalRenderNodes")) {
                external.add(parseExternalNode(node, fps));
            }
        } else if (root.has("externalRenderNodes") && root.get("externalRenderNodes").isArray()) {
            int fps = frameRateFromRoot(root);
            for (JsonNode node : root.get("externalRenderNodes")) {
                external.add(parseExternalNode(node, fps));
            }
        } else {
            external.addAll(parseExternalNodes(meta.get(META_EXTERNAL_NODES)));
        }

        List<TimelineMarker> markers = new ArrayList<>();
        if (root.has("markers") && root.get("markers").isArray()) {
            for (JsonNode m : root.get("markers")) {
                markers.add(new TimelineMarker(
                        m.path("id").asText("m"),
                        m.path("name").asText(""),
                        m.path("timeSeconds").asDouble(m.path("time").asDouble(0)),
                        textOrNull(m, "color"),
                        textOrNull(m, "comment")));
            }
        }

        Map<String, String> packaging = new LinkedHashMap<>();
        if (root.has("packaging") && root.get("packaging").isObject()) {
            root.get("packaging").fields().forEachRemaining(e ->
                    packaging.put(e.getKey(), e.getValue().asText("")));
        }

        return new TimelineExtensions(
                root.path("schemaVersion").asText("2.0"),
                composer,
                external,
                markers,
                List.of(),
                packaging,
                root.path("otio").path("roundTrip").path("lossy").asBoolean(false));
    }

    public void mergeIntoMetadata(Map<String, String> metadata, TimelineExtensions ext) {
        metadata.put("schemaVersion", ext.schemaVersion());
        metadata.put(META_FINAL_COMPOSER, ext.finalComposer().name().toLowerCase());
        if (!ext.externalRenderNodes().isEmpty()) {
            try {
                metadata.put(META_EXTERNAL_NODES, MAPPER.writeValueAsString(ext.externalRenderNodes()));
            } catch (Exception ignored) {
                // skip
            }
        }
        metadata.put(META_OTIO_LOSSY, String.valueOf(ext.otioExportLossy()));
    }

    private List<ExternalRenderNode> parseExternalNodes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<ExternalRenderNode>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private ExternalRenderNode parseExternalNode(JsonNode node) {
        return parseExternalNode(node, 30);
    }

    private ExternalRenderNode parseExternalNode(JsonNode node, int fps) {
        double start = node.has("timelineStart")
                ? node.path("timelineStart").asDouble(0)
                : rangeStartSec(node.path("timelineRange"), fps);
        double duration = node.has("duration")
                ? node.path("duration").asDouble(5)
                : rangeDurationSec(node.path("timelineRange"), fps);
        Map<String, Object> params = new LinkedHashMap<>();
        if (node.has("params") && node.get("params").isObject()) {
            params.putAll(MAPPER.convertValue(node.get("params"), new TypeReference<Map<String, Object>>() {}));
        }
        if (node.has("dependsOn") && node.get("dependsOn").isArray()) {
            List<String> deps = new ArrayList<>();
            node.get("dependsOn").forEach(d -> deps.add(d.asText()));
            params.put("dependsOn", deps);
        }
        JsonNode output = node.path("output");
        String format = output.path("format").asText("png_sequence");
        return new ExternalRenderNode(
                node.path("id").asText("xr"),
                node.path("backend").asText("blender"),
                textOrNull(node, "templateId"),
                textOrNull(node, "graphId"),
                textOrNull(node, "attachToClipId"),
                start,
                duration,
                params,
                format);
    }

    private static FinalComposerHint resolveFinalComposer(JsonNode root, Map<String, String> meta) {
        JsonNode renderGraph = root.path("renderGraph");
        if (renderGraph.has("finalComposer")) {
            JsonNode fc = renderGraph.get("finalComposer");
            if (fc.isTextual()) {
                return FinalComposerHint.fromString(fc.asText());
            }
            if (fc.isObject() && fc.has("selector")) {
                return FinalComposerHint.fromString(fc.get("selector").asText("auto"));
            }
        }
        if (root.has("finalComposer")) {
            return FinalComposerHint.fromString(root.get("finalComposer").asText());
        }
        return FinalComposerHint.fromString(meta.get(META_FINAL_COMPOSER));
    }

    private static int frameRateFromRoot(JsonNode root) {
        JsonNode rate = root.path("project").path("frameRate");
        if (rate.has("num") && rate.has("den") && rate.get("den").asInt(1) > 0) {
            return Math.max(1, rate.get("num").asInt(30) / rate.get("den").asInt(1));
        }
        return 30;
    }

    private static double rangeStartSec(JsonNode range, int fps) {
        if (range.isMissingNode()) {
            return 0;
        }
        JsonNode start = range.path("start");
        int frame = start.path("frame").asInt(0);
        return frame / (double) Math.max(1, fps);
    }

    private static double rangeDurationSec(JsonNode range, int fps) {
        if (range.isMissingNode()) {
            return 5;
        }
        JsonNode dur = range.path("duration");
        int frame = dur.path("frame").asInt(fps * 5);
        return frame / (double) Math.max(1, fps);
    }

    private List<TimelineMarker> parseMarkers(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<TimelineMarker>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<TimelineTransition> parseTransitions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<TimelineTransition>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, String> parsePackaging(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}

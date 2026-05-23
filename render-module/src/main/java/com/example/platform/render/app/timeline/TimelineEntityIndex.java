package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.internal.EntityKind;
import com.example.platform.render.domain.timeline.internal.EntityRef;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extracts stable-id entities from Internal Timeline 1.0 JSON for semantic diff.
 */
public final class TimelineEntityIndex {

    private TimelineEntityIndex() {}

    public static Map<String, JsonNode> indexAll(JsonNode root) {
        Map<String, JsonNode> index = new LinkedHashMap<>();
        indexInternal(root, index);
        return index;
    }

    private static void indexInternal(JsonNode root, Map<String, JsonNode> index) {
        if (root.has("project")) {
            put(index, EntityKind.PROJECT, "project", root.get("project"));
        }
        JsonNode registry = root.path("assetRegistry").path("assets");
        if (registry.isObject()) {
            registry.fields().forEachRemaining(e -> put(index, EntityKind.ASSET, e.getKey(), e.getValue()));
        }
        JsonNode composition = root.get("composition");
        if (composition != null) {
            JsonNode tracks = composition.get("tracks");
            if (tracks != null && tracks.isArray()) {
                for (JsonNode track : tracks) {
                    String trackId = track.path("id").asText("");
                    if (!trackId.isBlank()) {
                        put(index, EntityKind.TRACK, trackId, track);
                    }
                    JsonNode clips = track.get("clips");
                    if (clips != null && clips.isArray()) {
                        for (JsonNode clip : clips) {
                            put(index, EntityKind.CLIP, clip.path("id").asText("clip"), clip);
                        }
                    }
                }
            }
            JsonNode subtitleTracks = composition.get("subtitleTracks");
            if (subtitleTracks != null && subtitleTracks.isArray()) {
                for (JsonNode st : subtitleTracks) {
                    put(index, EntityKind.SUBTITLE_TRACK, st.path("id").asText("sub"), st);
                }
            }
            JsonNode transitions = composition.get("transitions");
            if (transitions != null && transitions.isArray()) {
                for (JsonNode tr : transitions) {
                    put(index, EntityKind.TRANSITION, tr.path("id").asText("tr"), tr);
                }
            }
            JsonNode buses = composition.get("audioBuses");
            if (buses != null && buses.isArray()) {
                for (JsonNode bus : buses) {
                    put(index, EntityKind.AUDIO_BUS, bus.path("id").asText("bus"), bus);
                }
            }
        }
        JsonNode styles = root.get("styles");
        if (styles != null && styles.isObject()) {
            styles.fields().forEachRemaining(e -> put(index, EntityKind.STYLE, e.getKey(), e.getValue()));
        }
        JsonNode templates = root.get("templates");
        if (templates != null && templates.isObject()) {
            templates.fields().forEachRemaining(e -> put(index, EntityKind.TEMPLATE, e.getKey(), e.getValue()));
        }
        JsonNode renderGraph = root.get("renderGraph");
        if (renderGraph != null) {
            JsonNode layers = renderGraph.get("layers");
            if (layers != null && layers.isArray()) {
                for (JsonNode layer : layers) {
                    put(index, EntityKind.LAYER, layer.path("id").asText("layer"), layer);
                }
            }
            JsonNode external = renderGraph.get("externalRenderNodes");
            if (external != null && external.isArray()) {
                for (JsonNode node : external) {
                    put(index, EntityKind.EXTERNAL_NODE, node.path("id").asText("ext"), node);
                }
            }
            if (renderGraph.has("finalComposer")) {
                put(index, EntityKind.FINAL_COMPOSER, "finalComposer", renderGraph.get("finalComposer"));
            }
            if (renderGraph.has("audioMix")) {
                put(index, EntityKind.AUDIO_MIX, renderGraph.path("audioMix").path("id").asText("mix_final"),
                        renderGraph.get("audioMix"));
            }
        }
        JsonNode outputs = root.get("outputs");
        if (outputs != null && outputs.isArray()) {
            for (JsonNode out : outputs) {
                put(index, EntityKind.OUTPUT, out.path("id").asText("output"), out);
            }
        }
        if (root.has("packaging")) {
            JsonNode pkg = root.get("packaging");
            String pkgId = pkg.path("id").asText("packaging");
            put(index, EntityKind.PACKAGING, pkgId, pkg);
        }
    }

    private static void put(Map<String, JsonNode> index, EntityKind kind, String id, JsonNode node) {
        if (id == null || id.isBlank() || node == null) {
            return;
        }
        index.put(key(kind, id), node);
    }

    public static String key(EntityKind kind, String id) {
        return new EntityRef(kind, id).key();
    }
}

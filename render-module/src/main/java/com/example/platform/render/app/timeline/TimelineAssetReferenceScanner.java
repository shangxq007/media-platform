package com.example.platform.render.app.timeline;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Scans Internal Timeline 1.0 JSON for references to a media {@code assetId}.
 */
public final class TimelineAssetReferenceScanner {

    private TimelineAssetReferenceScanner() {}

    public static List<AssetReferenceHit> findReferences(JsonNode root, String assetId) {
        List<AssetReferenceHit> hits = new ArrayList<>();
        if (root == null || assetId == null || assetId.isBlank()) {
            return hits;
        }
        JsonNode registry = root.path("assetRegistry").path("assets");
        if (registry.isObject() && registry.has(assetId)) {
            hits.add(new AssetReferenceHit("assetRegistry", assetId, "registry_entry"));
        }
        JsonNode tracks = root.path("composition").path("tracks");
        if (tracks.isArray()) {
            for (JsonNode track : tracks) {
                String trackId = track.path("id").asText("track");
                JsonNode clips = track.path("clips");
                if (!clips.isArray()) {
                    continue;
                }
                for (JsonNode clip : clips) {
                    if (assetId.equals(clip.path("assetId").asText(""))) {
                        hits.add(new AssetReferenceHit(
                                "composition.tracks", trackId + "/" + clip.path("id").asText("clip"), "clip"));
                    }
                }
            }
        }
        JsonNode layers = root.path("renderGraph").path("layers");
        if (layers.isArray()) {
            for (JsonNode layer : layers) {
                scanLayerForAsset(layer, assetId, hits);
            }
        }
        return hits;
    }

    public static Set<String> collectAssetIds(JsonNode root) {
        Set<String> ids = new LinkedHashSet<>();
        if (root == null) {
            return ids;
        }
        JsonNode registry = root.path("assetRegistry").path("assets");
        if (registry.isObject()) {
            registry.fieldNames().forEachRemaining(ids::add);
        }
        JsonNode tracks = root.path("composition").path("tracks");
        if (tracks.isArray()) {
            for (JsonNode track : tracks) {
                JsonNode clips = track.path("clips");
                if (clips.isArray()) {
                    for (JsonNode clip : clips) {
                        String assetId = clip.path("assetId").asText("");
                        if (!assetId.isBlank()) {
                            ids.add(assetId);
                        }
                    }
                }
            }
        }
        return ids;
    }

    private static void scanLayerForAsset(JsonNode layer, String assetId, List<AssetReferenceHit> hits) {
        if (layer.has("assetId") && assetId.equals(layer.path("assetId").asText(""))) {
            hits.add(new AssetReferenceHit("renderGraph.layers", layer.path("id").asText("layer"), "layer"));
        }
        if (layer.has("stickers") && layer.get("stickers").isArray()) {
            for (JsonNode sticker : layer.get("stickers")) {
                if (assetId.equals(sticker.path("assetId").asText(""))) {
                    hits.add(new AssetReferenceHit(
                            "renderGraph.layers.stickers",
                            layer.path("id").asText("layer"),
                            "sticker"));
                }
            }
        }
    }

    public record AssetReferenceHit(String path, String entityId, String kind) {}
}

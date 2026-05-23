package com.example.platform.render.domain.timeline;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reference to an external asset (media file) used in a timeline.
 */
public record TimelineAssetRef(
        String assetId,
        String storageUri,
        String format,
        long duration,
        int width,
        int height,
        Map<String, String> metadata) {

    public TimelineAssetRef {
        if (metadata == null) {
            metadata = Map.of();
        }
    }

    public static TimelineAssetRef of(String assetId, String storageUri) {
        return new TimelineAssetRef(assetId, storageUri, "unknown", 0, 0, 0, Map.of());
    }

    public TimelineAssetRef withMetadata(Map<String, String> extra) {
        Map<String, String> merged = new LinkedHashMap<>(metadata);
        merged.putAll(extra);
        return new TimelineAssetRef(assetId, storageUri, format, duration, width, height, Map.copyOf(merged));
    }
}

package com.example.platform.render.domain.timeline.compile;

import java.util.Map;

/**
 * Normalized reference to a source asset.
 *
 * <p>Asset references are internal to the platform. The storageUri is a platform-internal
 * locator (e.g., {@code asset://...} or {@code s3://...}), not a public URL.</p>
 *
 * @param assetId     canonical asset identifier
 * @param storageUri  platform-internal storage locator
 * @param format      media format (e.g., "mp4", "mov")
 * @param duration    asset duration in seconds
 * @param width       video width in pixels (0 if unknown)
 * @param height      video height in pixels (0 if unknown)
 * @param metadata    immutable metadata snapshot
 */
public record NormalizedAssetRef(
        String assetId,
        String storageUri,
        String format,
        long duration,
        int width,
        int height,
        Map<String, String> metadata) {

    /**
     * Creates a minimal asset ref with just ID and URI.
     */
    public static NormalizedAssetRef of(String assetId, String storageUri) {
        return new NormalizedAssetRef(assetId, storageUri, "unknown", 0, 0, 0, Map.of());
    }
}

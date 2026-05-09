package com.example.platform.render.domain.timeline;

/**
 * Reference to an external asset (media file) used in a timeline.
 *
 * @param assetId    unique asset identifier (e.g., artifact ID or storage URI)
 * @param storageUri URI where the asset content is stored
 * @param format     media format (e.g., "mp4", "mov", "wav")
 * @param duration   asset duration in seconds
 * @param width      video width in pixels (0 for audio-only)
 * @param height     video height in pixels (0 for audio-only)
 */
public record TimelineAssetRef(
        String assetId,
        String storageUri,
        String format,
        long duration,
        int width,
        int height) {

    /**
     * Creates a minimal asset reference with just the ID and URI.
     */
    public static TimelineAssetRef of(String assetId, String storageUri) {
        return new TimelineAssetRef(assetId, storageUri, "unknown", 0, 0, 0);
    }
}

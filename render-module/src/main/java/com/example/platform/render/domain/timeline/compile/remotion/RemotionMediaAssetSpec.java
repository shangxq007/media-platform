package com.example.platform.render.domain.timeline.compile.remotion;

/**
 * Remotion media asset reference — safe internal reference only.
 * No local paths, no storage internals.
 * Internal only.
 */
public record RemotionMediaAssetSpec(
        String assetId,
        String mediaType,
        String format,
        double durationSeconds,
        int width,
        int height) {}

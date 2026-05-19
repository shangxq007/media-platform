package com.example.platform.render.infrastructure;

import java.util.Set;

/**
 * A named render profile that maps to provider capabilities and constraints.
 *
 * @param name           profile identifier (e.g., "pro_1080p", "free_720p_watermarked")
 * @param description    human-readable description
 * @param resolution     output resolution (e.g., "1920x1080")
 * @param frameRate      target frame rate
 * @param format         output container format
 * @param videoCodec     video codec
 * @param audioCodec     audio codec
 * @param maxDurationSec maximum allowed duration in seconds
 * @param watermark      whether to apply watermark
 * @param requiredTier   minimum user tier required
 * @param allowedEffects effect keys allowed in this profile
 * @param experimental   whether this profile uses experimental providers
 */
public record RenderProviderProfile(
        String name,
        String description,
        String resolution,
        int frameRate,
        String format,
        String videoCodec,
        String audioCodec,
        int maxDurationSec,
        boolean watermark,
        String requiredTier,
        Set<String> allowedEffects,
        boolean experimental
) {
    public int width() {
        return Integer.parseInt(resolution.split("x")[0]);
    }

    public int height() {
        return Integer.parseInt(resolution.split("x")[1]);
    }

    public boolean allowsEffect(String effectKey) {
        return allowedEffects.contains(effectKey);
    }
}

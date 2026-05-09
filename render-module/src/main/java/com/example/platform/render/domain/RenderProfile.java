package com.example.platform.render.domain;

import java.util.Map;

/**
 * Render profile defining output parameters for a render job.
 *
 * <p>Profiles encapsulate resolution, codec, bitrate, and other output settings.
 * They are referenced by {@link RenderPlan} and {@link RenderStep} instances.</p>
 *
 * @param id          unique profile identifier (e.g., "social_1080p", "broadcast_4k")
 * @param label       human-readable display name
 * @param description description of the profile's intended use
 * @param resolution  output resolution (e.g., "1920x1080")
 * @param codec       video codec (e.g., "h264", "h265")
 * @param bitrateKbps video bitrate in kbps
 * @param audioCodec  audio codec (e.g., "aac")
 * @param audioRate   audio sample rate in Hz
 * @param extraParams additional codec-specific parameters
 */
public record RenderProfile(
        String id,
        String label,
        String description,
        String resolution,
        String codec,
        int bitrateKbps,
        String audioCodec,
        int audioRate,
        Map<String, String> extraParams) {

    /**
     * Creates a simple profile with minimal parameters.
     */
    public static RenderProfile of(String id, String resolution, String codec) {
        return new RenderProfile(id, id, null, resolution, codec, 0, "aac", 48000, Map.of());
    }

    /** Social media 1080p profile. */
    public static RenderProfile social1080p() {
        return new RenderProfile(
                "social_1080p", "Social 1080p", "Social media optimized 1080p",
                "1920x1080", "h264", 8000, "aac", 48000, Map.of());
    }

    /** Social media 720p profile. */
    public static RenderProfile social720p() {
        return new RenderProfile(
                "social_720p", "Social 720p", "Social media optimized 720p",
                "1280x720", "h264", 4000, "aac", 48000, Map.of());
    }
}

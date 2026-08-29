package com.example.platform.render.infrastructure;

import java.util.Locale;
import java.util.Map;

/**
 * ROADMAP_18 (CI44): one-way provider observation -> raw probe observation.
 * Raw strings remain provider observation INPUT only; canonical semantics live
 * in color-image-module. No silent concrete probe-field-to-color inference; no HDR boolean.
 */
public final class ColorProbeMetadataExtractor {

    private ColorProbeMetadataExtractor() {
    }

    /** Raw provider observation extraction (strings preserved as observation, not canonical authority). */
    public static ColorProbeMetadata fromStreamMetadata(Map<String, String> metadata, String pixelFormat) {
        if (metadata == null) {
            metadata = Map.of();
        }
        String space = firstNonBlank(metadata,
                "color_space", "colorspace", "colorSpace");
        String primaries = firstNonBlank(metadata,
                "color_primaries", "colorPrimaries");
        String transfer = firstNonBlank(metadata,
                "color_transfer", "colorTransfer", "color_trc");
        String range = firstNonBlank(metadata,
                "color_range", "colorRange", "chroma_location");
        String pixFmt = pixelFormat != null && !pixelFormat.isBlank()
                ? pixelFormat.trim()
                : firstNonBlank(metadata, "pixel_format", "pixelFormat");
        return new ColorProbeMetadata(
                nullToEmpty(space),
                nullToEmpty(primaries),
                nullToEmpty(transfer),
                nullToEmpty(range),
                nullToEmpty(pixFmt));
    }

    private static String firstNonBlank(Map<String, String> metadata, String... keys) {
        for (String key : keys) {
            String v = metadata.get(key);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}

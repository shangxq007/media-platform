package com.example.platform.render.infrastructure;

import java.util.Locale;
import java.util.Map;

/**
 * Maps FFmpeg / JavaCV stream metadata to {@link ColorProbeMetadata}.
 */
public final class ColorProbeMetadataExtractor {

    private ColorProbeMetadataExtractor() {
    }

    public static ColorProbeMetadata fromStreamMetadata(Map<String, String> metadata, String pixelFormat) {
        if (metadata == null || metadata.isEmpty()) {
            return inferFromPixelFormat(pixelFormat);
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
                ? pixelFormat
                : firstNonBlank(metadata, "pix_fmt", "pixel_format");
        boolean hdr = isHdrTransfer(transfer) || isHdrPrimaries(primaries)
                || is10BitPixelFormat(pixFmt);
        return new ColorProbeMetadata(
                nullToEmpty(space),
                nullToEmpty(primaries),
                nullToEmpty(transfer),
                nullToEmpty(range),
                nullToEmpty(pixFmt),
                hdr);
    }

    private static ColorProbeMetadata inferFromPixelFormat(String pixelFormat) {
        if (pixelFormat == null || pixelFormat.isBlank()) {
            return ColorProbeMetadata.empty();
        }
        return new ColorProbeMetadata("", "", "", "", pixelFormat, is10BitPixelFormat(pixelFormat));
    }

    private static boolean isHdrTransfer(String transfer) {
        if (transfer == null) {
            return false;
        }
        String t = transfer.toLowerCase(Locale.ROOT);
        return t.contains("smpte2084") || t.contains("2084")
                || t.contains("pq") || t.contains("hlg")
                || t.contains("arib-std-b67");
    }

    private static boolean isHdrPrimaries(String primaries) {
        if (primaries == null) {
            return false;
        }
        String p = primaries.toLowerCase(Locale.ROOT);
        return p.contains("bt2020") || p.contains("2020");
    }

    private static boolean is10BitPixelFormat(String pixFmt) {
        if (pixFmt == null) {
            return false;
        }
        String p = pixFmt.toLowerCase(Locale.ROOT);
        return p.contains("10le") || p.contains("10be") || p.contains("p010")
                || p.contains("yuv420p10") || p.contains("yuv422p10");
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

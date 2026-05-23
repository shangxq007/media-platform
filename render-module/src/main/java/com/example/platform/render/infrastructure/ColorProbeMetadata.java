package com.example.platform.render.infrastructure;

/**
 * Color / HDR fields extracted from container or stream metadata (ffprobe-style keys).
 */
public record ColorProbeMetadata(
        String colorSpace,
        String colorPrimaries,
        String colorTransfer,
        String colorRange,
        String pixelFormat,
        boolean hdr) {

    public static ColorProbeMetadata empty() {
        return new ColorProbeMetadata("", "", "", "", "", false);
    }

    public java.util.Map<String, String> toTimelineMetadata() {
        java.util.Map<String, String> meta = new java.util.LinkedHashMap<>();
        if (colorSpace != null && !colorSpace.isBlank()) {
            meta.put("platform.color.space", colorSpace);
        }
        if (colorPrimaries != null && !colorPrimaries.isBlank()) {
            meta.put("platform.color.primaries", colorPrimaries);
        }
        if (colorTransfer != null && !colorTransfer.isBlank()) {
            meta.put("platform.color.transfer", colorTransfer);
        }
        if (colorRange != null && !colorRange.isBlank()) {
            meta.put("platform.color.range", colorRange);
        }
        if (pixelFormat != null && !pixelFormat.isBlank()) {
            meta.put("platform.color.pixelFormat", pixelFormat);
        }
        meta.put("platform.color.hdr", String.valueOf(hdr));
        return meta;
    }
}

package com.example.platform.render.infrastructure;

/**
 * Color / HDR fields extracted from container or stream metadata (media probe-style keys).
 */
public record ColorProbeMetadata(
        String colorSpace,
        String colorPrimaries,
        String colorTransfer,
        String colorRange,
        String pixelFormat) {

    public static ColorProbeMetadata empty() {
        return new ColorProbeMetadata("", "", "", "", "");
    }

}

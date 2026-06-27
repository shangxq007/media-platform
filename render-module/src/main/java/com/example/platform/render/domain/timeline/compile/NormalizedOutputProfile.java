package com.example.platform.render.domain.timeline.compile;

/**
 * Normalized output profile for render.
 *
 * <p>Deterministic output specification derived from TimelineOutputSpec.
 * All fields have explicit defaults for reproducibility.</p>
 *
 * @param format       output container format (e.g., "mp4")
 * @param resolution   output resolution string (e.g., "1920x1080")
 * @param frameRate    output frame rate
 * @param videoCodec   video codec (e.g., "h264")
 * @param videoBitrate video bitrate in kbps
 * @param audioCodec   audio codec (e.g., "aac")
 * @param sampleRate   audio sample rate in Hz
 * @param channels     audio channels (1=mono, 2=stereo)
 * @param audioBitrate audio bitrate in kbps
 * @param pixelFormat  pixel format (e.g., "yuv420p")
 */
public record NormalizedOutputProfile(
        String format,
        String resolution,
        double frameRate,
        String videoCodec,
        int videoBitrate,
        String audioCodec,
        int sampleRate,
        int channels,
        int audioBitrate,
        String pixelFormat) {

    /**
     * Default MP4 1080p30 profile.
     */
    public static final NormalizedOutputProfile DEFAULT_MP4_1080P30 = new NormalizedOutputProfile(
            "mp4", "1920x1080", 30.0, "h264", 8000,
            "aac", 48000, 2, 128, "yuv420p");

    /**
     * Parses the width from the resolution string.
     */
    public int width() {
        if (resolution != null && resolution.contains("x")) {
            try {
                return Integer.parseInt(resolution.split("x")[0]);
            } catch (NumberFormatException e) {
                return 1920;
            }
        }
        return 1920;
    }

    /**
     * Parses the height from the resolution string.
     */
    public int height() {
        if (resolution != null && resolution.contains("x")) {
            try {
                return Integer.parseInt(resolution.split("x")[1]);
            } catch (NumberFormatException e) {
                return 1080;
            }
        }
        return 1080;
    }
}

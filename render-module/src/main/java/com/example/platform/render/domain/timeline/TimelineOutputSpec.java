package com.example.platform.render.domain.timeline;

/**
 * Output specification for a timeline render.
 *
 * @param format       output format (e.g., "mp4", "mov", "webm")
 * @param resolution   output resolution (e.g., "1920x1080")
 * @param frameRate    output frame rate (e.g., 30.0)
 * @param videoCodec   video codec (e.g., "h264", "h265")
 * @param videoBitrate video bitrate in kbps
 * @param audioSpec    audio output specification
 * @param pixelFormat  pixel format (e.g., "yuv420p")
 */
public record TimelineOutputSpec(
        String format,
        String resolution,
        double frameRate,
        String videoCodec,
        int videoBitrate,
        TimelineAudioSpec audioSpec,
        String pixelFormat) {

    /**
     * Creates a default MP4 output spec at 1080p30.
     */
    public static TimelineOutputSpec mp4_1080p30() {
        return new TimelineOutputSpec(
                "mp4", "1920x1080", 30.0, "h264", 8000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
    }

    /**
     * Creates a default MP4 output spec at 720p30.
     */
    public static TimelineOutputSpec mp4_720p30() {
        return new TimelineOutputSpec(
                "mp4", "1280x720", 30.0, "h264", 4000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
    }

    /**
     * Parses the width from the resolution string.
     */
    public int width() {
        if (resolution != null && resolution.contains("x")) {
            return Integer.parseInt(resolution.split("x")[0]);
        }
        return 0;
    }

    /**
     * Parses the height from the resolution string.
     */
    public int height() {
        if (resolution != null && resolution.contains("x")) {
            return Integer.parseInt(resolution.split("x")[1]);
        }
        return 0;
    }
}

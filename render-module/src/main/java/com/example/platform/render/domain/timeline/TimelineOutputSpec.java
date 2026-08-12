package com.example.platform.render.domain.timeline;

import com.example.platform.render.domain.timeline.semantics.time.FrameRate;

/**
 * Output specification for a timeline render.
 *
 * @param format       output format (e.g., "mp4", "mov", "webm")
 * @param resolution   output resolution (e.g., "1920x1080")
 * @param frameRate    output frame rate (C1-CNM1: EXACT rational, e.g. 30/1,
 *                     24000/1001 for 23.976) — never a binary floating value
 * @param videoCodec   video codec (e.g., "h264", "h265")
 * @param videoBitrate video bitrate in kbps
 * @param audioSpec    audio output specification
 * @param pixelFormat  pixel format (e.g., "yuv420p")
 */
public record TimelineOutputSpec(
        String format,
        String resolution,
        FrameRate frameRate,
        String videoCodec,
        int videoBitrate,
        TimelineAudioSpec audioSpec,
        String pixelFormat) {

    /**
     * Creates a default MP4 output spec at 1080p30.
     */
    public static TimelineOutputSpec mp4_1080p30() {
        return new TimelineOutputSpec(
                "mp4", "1920x1080", FrameRate.of(30, 1), "h264", 8000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
    }

    /**
     * Creates a default MP4 output spec at 720p30.
     */
    public static TimelineOutputSpec mp4_720p30() {
        return new TimelineOutputSpec(
                "mp4", "1280x720", FrameRate.of(30, 1), "h264", 4000,
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

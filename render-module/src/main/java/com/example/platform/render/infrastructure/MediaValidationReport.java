package com.example.platform.render.infrastructure;

import java.util.List;

/**
 * Result of media validation / quality check.
 *
 * @param jobId       the render job identifier
 * @param valid       whether the output passed validation
 * @param message     status message
 * @param filePath    path to the output file
 * @param fileSize    file size in bytes
 * @param durationMs  duration in milliseconds
 * @param width       video width
 * @param height      video height
 * @param videoCodec  video codec name
 * @param audioCodec  audio codec name
 * @param frameRate   frame rate
 * @param videoBitrate video bitrate
 * @param audioChannels number of audio channels
 * @param sampleRate  audio sample rate
 * @param warnings    list of warnings (non-fatal issues)
 */
public record MediaValidationReport(
        String jobId,
        boolean valid,
        String message,
        String filePath,
        long fileSize,
        double durationMs,
        int width,
        int height,
        String videoCodec,
        String audioCodec,
        double frameRate,
        long videoBitrate,
        int audioChannels,
        int sampleRate,
        List<String> warnings
) {
    public static MediaValidationReport failed(String jobId, String message) {
        return new MediaValidationReport(jobId, false, message, "", 0, 0, 0, 0,
                "", "", 0, 0, 0, 0, List.of(message));
    }

    public boolean hasVideo() {
        return width > 0 && height > 0 && videoCodec != null && !videoCodec.isEmpty();
    }

    public boolean hasAudio() {
        return audioChannels > 0 && audioCodec != null && !audioCodec.isEmpty();
    }

    public boolean matchesResolution(int expectedWidth, int expectedHeight) {
        return width == expectedWidth && height == expectedHeight;
    }

    public boolean matchesDuration(double expectedMs, double toleranceMs) {
        return Math.abs(durationMs - expectedMs) <= toleranceMs;
    }

    public boolean matchesCodec(String expectedVideoCodec) {
        return expectedVideoCodec == null || expectedVideoCodec.equalsIgnoreCase(videoCodec);
    }
}

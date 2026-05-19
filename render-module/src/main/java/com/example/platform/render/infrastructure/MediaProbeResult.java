package com.example.platform.render.infrastructure;

import java.util.List;

public record MediaProbeResult(
        String jobId,
        boolean valid,
        String filePath,
        long fileSizeBytes,
        double durationMs,
        int width,
        int height,
        String videoCodec,
        String audioCodec,
        double frameRate,
        long bitrate,
        int audioChannels,
        int sampleRate,
        List<String> warnings,
        String errorMessage
) {
    public static MediaProbeResult failed(String jobId, String errorMessage) {
        return new MediaProbeResult(jobId, false, "", 0, 0, 0, 0,
                "", "", 0, 0, 0, 0, List.of(errorMessage), errorMessage);
    }

    public boolean hasVideo() {
        return width > 0 && height > 0 && videoCodec != null && !videoCodec.isEmpty();
    }

    public boolean hasAudio() {
        return audioChannels > 0 && audioCodec != null && !audioCodec.isEmpty();
    }
}

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
        String errorMessage,
        ColorProbeMetadata color) {

    public MediaProbeResult {
        if (color == null) {
            color = ColorProbeMetadata.empty();
        }
    }

    public static MediaProbeResult failed(String jobId, String errorMessage) {
        return new MediaProbeResult(jobId, false, "", 0, 0, 0, 0,
                "", "", 0, 0, 0, 0, List.of(errorMessage), errorMessage, ColorProbeMetadata.empty());
    }

    public boolean hasVideo() {
        return width > 0 && height > 0 && videoCodec != null && !videoCodec.isEmpty();
    }

    /**
     * Returns true if the media has at least one audio stream.
     */
    public boolean hasAudioStream() {
        return audioChannels > 0
                || (audioCodec != null && !audioCodec.isEmpty());
    }

    /**
     * Returns true if the media has audio usable for processing
     * (mixing, waveform, auto-captions). Requires both codec and channels.
     */
    public boolean hasUsableAudio() {
        return hasAudioStream()
                && audioCodec != null && !audioCodec.isBlank()
                && audioChannels > 0;
    }
}

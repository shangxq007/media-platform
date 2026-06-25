package com.example.platform.render.domain.asset.semantic;

import java.util.List;

/**
 * Technical probe metadata extracted from media files via ffprobe.
 */
public record ProbeMetadata(
        String format,
        long sizeBytes,
        double durationSec,
        double fps,
        double frameCount,
        int width,
        int height,
        String videoCodec,
        String videoProfile,
        String pixelFormat,
        String colorSpace,
        String colorRange,
        int videoBitrate,
        int audioChannels,
        String audioChannelLayout,
        int audioSampleRate,
        int audioBitrate,
        String audioCodec,
        int audioBitDepth,
        int containerStreamCount,
        List<StreamSummary> streams,
        String errorCode,
        String errorMessage) {

    public record StreamSummary(String codecType, String codecName, int width, int height,
                                  double fps, int channels, int sampleRate) {}

    public static ProbeMetadata empty() {
        return new ProbeMetadata(null, 0, 0, 0, 0, 0, 0, null, null, null, null, null,
                0, 0, null, 0, 0, null, 0, 0, List.of(), null, null);
    }

    public boolean isValid() {
        return errorCode == null && format != null && durationSec > 0;
    }

    public ProbeMetadata withError(String errorCode, String errorMessage) {
        return new ProbeMetadata(format, sizeBytes, durationSec, fps, frameCount, width, height,
                videoCodec, videoProfile, pixelFormat, colorSpace, colorRange, videoBitrate,
                audioChannels, audioChannelLayout, audioSampleRate, audioBitrate, audioCodec,
                audioBitDepth, containerStreamCount, streams, errorCode, errorMessage);
    }
}

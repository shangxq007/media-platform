package com.example.platform.render.domain.asset.semantic;

import java.util.List;

/**
 * ASR result produced by a speech-to-text provider (Whisper).
 */
public record AsrResult(
        String provider,
        String model,
        String language,
        double durationSec,
        double processingTimeSec,
        String fullTranscript,
        List<AsrSegment> segments) {

    public record AsrSegment(
            long startMs,
            long endMs,
            String text,
            double confidence) {}

    public boolean isValid() {
        return fullTranscript != null && !fullTranscript.isBlank() && segments != null;
    }
}

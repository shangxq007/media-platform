package com.example.platform.ai.api.video;

import java.util.List;

public interface SilenceDetectionPort {

    SilenceDetectionResult detectSilence(SilenceDetectionRequest request);

    record SilenceDetectionRequest(
            String audioUri,
            double thresholdDb,
            int minSilenceDurationMs) {}

    record SilenceDetectionResult(
            double totalDurationSeconds,
            double silenceDurationSeconds,
            double silencePercentage,
            List<SilenceRegion> regions) {}

    record SilenceRegion(int startTimeMs, int endTimeMs, double durationMs, double avgDb) {}
}

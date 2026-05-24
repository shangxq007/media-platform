package com.example.platform.ai.api.video;

import java.util.List;

public interface HighlightDetectionPort {

    HighlightDetectionResult detectHighlights(HighlightDetectionRequest request);

    record HighlightDetectionRequest(
            String videoUri,
            String audioUri,
            List<String> subtitleTexts,
            int maxHighlights,
            int minHighlightDurationMs,
            int maxHighlightDurationMs) {}

    record HighlightDetectionResult(
            List<HighlightSegment> highlights,
            String summary) {}

    record HighlightSegment(
            int startTimeMs,
            int endTimeMs,
            double score,
            String reason,
            String label) {}
}

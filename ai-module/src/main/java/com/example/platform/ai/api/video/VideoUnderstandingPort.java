package com.example.platform.ai.api.video;

import java.util.List;

public interface VideoUnderstandingPort {

    VideoUnderstandingResult analyze(VideoUnderstandingRequest request);

    record VideoUnderstandingRequest(
            String videoUri,
            String audioUri,
            List<String> subtitleTexts,
            int sampleIntervalSeconds,
            String analysisType) {}

    record VideoUnderstandingResult(
            String summary,
            List<SceneDescription> scenes,
            List<ContentTag> tags,
            List<ShotBoundary> shotBoundaries) {}

    record SceneDescription(
            int startTimeMs,
            int endTimeMs,
            String description,
            List<String> detectedObjects) {}

    record ContentTag(String tag, double confidence, String category) {}

    record ShotBoundary(int timeMs, String transitionType, double confidence) {}
}

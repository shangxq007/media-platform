package com.example.platform.ai.api.video;

import java.util.List;

public interface SpeechToTextPort {

    SpeechToTextResult transcribe(TranscribeRequest request);

    record TranscribeRequest(
            String audioUri,
            String language,
            boolean wordTimestamps,
            int maxSegmentDurationMs) {}

    record SpeechToTextResult(
            String language,
            double durationSeconds,
            List<SubtitleSegment> segments) {}

    record SubtitleSegment(
            int index,
            int startTimeMs,
            int endTimeMs,
            String text,
            String language,
            double confidence,
            List<WordTiming> words) {}

    record WordTiming(String word, int startTimeMs, int endTimeMs, double probability) {}
}

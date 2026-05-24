package com.example.platform.ai.api.video;

import java.util.List;

public interface SubtitleTranslationPort {

    TranslationResult translate(TranslationRequest request);

    record TranslationRequest(
            List<SubtitleInput> segments,
            String sourceLanguage,
            String targetLanguage) {}

    record SubtitleInput(int index, int startTimeMs, int endTimeMs, String text) {}

    record TranslationResult(
            String targetLanguage,
            List<TranslatedSegment> segments) {}

    record TranslatedSegment(
            int index,
            int startTimeMs,
            int endTimeMs,
            String originalText,
            String translatedText,
            double confidence) {}
}

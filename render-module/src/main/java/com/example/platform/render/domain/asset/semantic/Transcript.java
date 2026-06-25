package com.example.platform.render.domain.asset.semantic;

import java.util.List;

/**
 * ASR transcript produced by a speech-to-text provider.
 */
public record Transcript(
        String transcriptId,
        String provider,
        String language,
        double confidence,
        String text,
        List<TranscriptSegment> segments) {

    public static Transcript of(String provider, String language, String text,
                                   double confidence, List<TranscriptSegment> segments) {
        return new Transcript("tx_" + System.currentTimeMillis(), provider, language,
                confidence, text, segments != null ? segments : List.of());
    }
}

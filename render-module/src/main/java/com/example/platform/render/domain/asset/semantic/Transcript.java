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

    /**
     * Create a Transcript from an AsrResult.
     */
    public static Transcript fromAsrResult(AsrResult result) {
        List<TranscriptSegment> segments = result.segments().stream()
                .map(s -> new TranscriptSegment(s.startMs(), s.endMs(), null, s.text()))
                .toList();
        return new Transcript("tx_" + System.currentTimeMillis(), result.provider(),
                result.language(), 0.9, result.fullTranscript(), segments);
    }
}

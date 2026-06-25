package com.example.platform.render.domain.asset.semantic;

/**
 * A timed segment within a transcript, with optional speaker label.
 */
public record TranscriptSegment(
        long startTimeMs,
        long endTimeMs,
        String speaker,
        String text) {
}

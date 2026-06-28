package com.example.platform.render.domain.caption;

/**
 * A single caption segment — text with timing.
 * Internal domain model.
 *
 * @param startMs  start time in milliseconds (>= 0)
 * @param endMs    end time in milliseconds (> startMs)
 * @param text     caption text (not blank)
 */
public record CaptionSegmentSpec(
        long startMs,
        long endMs,
        String text) {

    public double startSeconds() { return startMs / 1000.0; }
    public double endSeconds() { return endMs / 1000.0; }
    public double durationSeconds() { return endSeconds() - startSeconds(); }
}

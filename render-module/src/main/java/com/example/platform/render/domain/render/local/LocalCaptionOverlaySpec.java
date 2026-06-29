package com.example.platform.render.domain.render.local;

import java.util.Objects;

/**
 * Safe, typed specification for a caption overlay extracted from a BasicRenderPlan.
 *
 * <p>This is a platform-owned internal type. It captures only safe fields
 * from the plan's APPLY_CAPTION_OVERLAY step. Raw filtergraph, ASS style,
 * font paths, and external subtitle paths are NOT included.</p>
 *
 * @param captionId   caption identifier from the plan
 * @param text        sanitized caption text (non-blank, bounded length)
 * @param startMs     start time in milliseconds (>= 0)
 * @param endMs       end time in milliseconds (> startMs)
 */
public record LocalCaptionOverlaySpec(
        String captionId,
        String text,
        double startMs,
        double endMs
) {
    public static final int MAX_TEXT_LENGTH = 200;
    public static final double MAX_DURATION_MS = 300_000; // 5 minutes

    public LocalCaptionOverlaySpec {
        Objects.requireNonNull(captionId, "captionId must not be null");
        Objects.requireNonNull(text, "text must not be null");
        if (text.isBlank()) throw new IllegalArgumentException("caption text must not be blank");
        if (text.length() > MAX_TEXT_LENGTH)
            throw new IllegalArgumentException("caption text exceeds max length: " + text.length());
        if (startMs < 0) throw new IllegalArgumentException("startMs must be >= 0");
        if (endMs <= startMs) throw new IllegalArgumentException("endMs must be > startMs");
        if (endMs - startMs > MAX_DURATION_MS)
            throw new IllegalArgumentException("caption duration exceeds max: " + (endMs - startMs) + "ms");
    }

    /**
     * Duration in milliseconds.
     */
    public double durationMs() {
        return endMs - startMs;
    }

    /**
     * Start time in seconds (for FFmpeg/libass).
     */
    public double startSec() {
        return startMs / 1000.0;
    }

    /**
     * End time in seconds (for FFmpeg/libass).
     */
    public double endSec() {
        return endMs / 1000.0;
    }
}

package com.example.platform.render.domain.timeline.compile.remotion;

/**
 * Remotion composition metadata.
 * Internal only.
 */
public record RemotionCompositionSpec(
        String compositionId,
        int width,
        int height,
        double fps,
        int durationInFrames,
        double durationSeconds) {}

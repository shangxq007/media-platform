package com.example.platform.render.domain.timeline.compile.remotion;

/**
 * Remotion clip specification.
 * Internal only.
 */
public record RemotionClipSpec(
        String clipId,
        String assetId,
        double startSeconds,
        double durationSeconds,
        double assetInPoint,
        double assetOutPoint) {}

package com.example.platform.render.domain.timeline.compile.remotion;

/**
 * Remotion output specification.
 * Internal only.
 */
public record RemotionOutputSpec(
        String outputProfile,
        int width,
        int height,
        double fps,
        String container,
        String codecIntent) {}

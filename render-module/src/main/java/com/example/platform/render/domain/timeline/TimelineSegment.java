package com.example.platform.render.domain.timeline;

/**
 * A renderable segment window for incremental cache.
 */
public record TimelineSegment(
        String id,
        int startFrame,
        int durationFrames,
        String cacheKey) {}

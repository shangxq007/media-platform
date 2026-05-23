package com.example.platform.render.domain.timeline;

/**
 * Timeline segment cache policy from {@code renderGraph.segmentPolicy}.
 */
public record SegmentPolicy(
        boolean enabled,
        int segmentDurationFrames,
        int overlapFrames,
        String cacheScope) {

    public static SegmentPolicy disabled() {
        return new SegmentPolicy(false, 0, 0, "SEGMENT");
    }
}

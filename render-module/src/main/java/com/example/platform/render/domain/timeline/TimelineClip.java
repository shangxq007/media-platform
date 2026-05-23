package com.example.platform.render.domain.timeline;

import java.util.List;

/**
 * A clip within a timeline track.
 *
 * <p>Each clip references an asset and defines the in/out points within that
 * asset, as well as the position on the timeline.</p>
 *
 * @param id              unique clip identifier
 * @param assetRef        reference to the source asset
 * @param timelineStart   start position on the timeline in seconds
 * @param assetInPoint    in-point within the asset in seconds
 * @param assetOutPoint   out-point within the asset in seconds
 * @param clipDuration    duration of the clip on the timeline in seconds
 * @param effects         clip-level effects (filters, transitions, subtitles)
 */
public record TimelineClip(
        String id,
        TimelineAssetRef assetRef,
        double timelineStart,
        double assetInPoint,
        double assetOutPoint,
        double clipDuration,
        List<TimelineClipEffect> effects) {

    /**
     * Creates a simple clip with no effects.
     */
    public static TimelineClip of(String id, TimelineAssetRef assetRef,
            double timelineStart, double assetInPoint, double assetOutPoint) {
        double duration = assetOutPoint - assetInPoint;
        return new TimelineClip(id, assetRef, timelineStart, assetInPoint, assetOutPoint,
                duration, List.of());
    }

    /**
     * Returns {@code true} if the clip has valid timing (out > in, duration > 0).
     */
    public boolean hasValidTiming() {
        return assetOutPoint > assetInPoint && clipDuration > 0;
    }
}

package com.example.platform.render.domain.timeline.compile;

/**
 * Normalized clip within a track.
 *
 * <p>Each clip references an asset and defines timing on the timeline.
 * v0 does not support clip effects — unsupported effects fail closed.</p>
 *
 * @param clipId          source clip identifier
 * @param assetRef        reference to the source asset
 * @param timelineStart   start position on the timeline in seconds
 * @param assetInPoint    in-point within the asset in seconds
 * @param assetOutPoint   out-point within the asset in seconds
 * @param clipDuration    duration of the clip on the timeline in seconds
 */
public record NormalizedClip(
        String clipId,
        NormalizedAssetRef assetRef,
        double timelineStart,
        double assetInPoint,
        double assetOutPoint,
        double clipDuration) {

    /**
     * Returns true if the clip has valid timing.
     */
    public boolean hasValidTiming() {
        return assetOutPoint > assetInPoint && clipDuration > 0;
    }
}

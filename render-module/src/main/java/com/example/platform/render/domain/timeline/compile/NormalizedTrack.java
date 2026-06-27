package com.example.platform.render.domain.timeline.compile;

import java.util.List;

/**
 * Normalized track within a timeline.
 *
 * <p>Tracks are ordered by layer (ascending) and type priority (VIDEO > AUDIO > SUBTITLE).
 * Clips within a track are ordered by timelineStart (ascending).</p>
 *
 * @param trackId   source track identifier
 * @param name      track name
 * @param type      track type: VIDEO, AUDIO, SUBTITLE
 * @param layer     compositing layer (lower = behind)
 * @param muted     whether the track is muted
 * @param clips     ordered list of normalized clips
 */
public record NormalizedTrack(
        String trackId,
        String name,
        TrackType type,
        int layer,
        boolean muted,
        List<NormalizedClip> clips) {

    /**
     * Returns the total duration of this track (end of last clip).
     */
    public double totalDuration() {
        if (clips == null || clips.isEmpty()) {
            return 0;
        }
        return clips.stream()
                .mapToDouble(c -> c.timelineStart() + c.clipDuration())
                .max()
                .orElse(0);
    }

    /**
     * Track type enumeration.
     */
    public enum TrackType {
        VIDEO,
        AUDIO,
        SUBTITLE
    }
}

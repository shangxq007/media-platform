package com.example.platform.render.domain.timeline;

import java.util.List;

/**
 * A track within a timeline.
 *
 * <p>Tracks are ordered containers for clips. Each track has a type (video, audio,
 * subtitle) and a layer for compositing order.</p>
 *
 * @param id     unique track identifier
 * @param name   human-readable track name
 * @param type   track type (video, audio, subtitle)
 * @param layer  compositing layer (lower = behind)
 * @param clips  ordered list of clips in this track
 * @param muted  whether the track is muted
 * @param locked whether the track is locked for editing
 */
public record TimelineTrack(
        String id,
        String name,
        TrackType type,
        int layer,
        List<TimelineClip> clips,
        boolean muted,
        boolean locked) {

    /**
     * Creates a simple track with no clips.
     */
    public static TimelineTrack of(String id, String name, TrackType type) {
        return new TimelineTrack(id, name, type, 0, List.of(), false, false);
    }

    /**
     * Returns the total duration of this track (end of last clip).
     */
    public double totalDuration() {
        if (clips.isEmpty()) {
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

package com.example.platform.render.domain.timeline.canonical;

import java.util.List;

/**
 * Canonical Timeline Track - stable entity with trackId.
 */
public record TimelineTrack(
        String trackId,
        String name,
        TrackType type,
        List<TimelineClip> clips) {

    public TimelineTrack {
        if (trackId == null || trackId.isBlank()) {
            throw new IllegalArgumentException("trackId must not be blank");
        }
        if (type == null) type = TrackType.VIDEO;
        if (clips == null) clips = List.of();
        clips = List.copyOf(clips);
    }
}

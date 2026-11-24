package com.example.platform.timeline.canonical;

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
        if (type != TrackType.VIDEO && clips.stream().anyMatch(clip -> !clip.getTransform().equals(
                com.example.platform.timeline.semantics.effect.VideoTransform.IDENTITY))) {
            throw new IllegalArgumentException("Video transform requires a video track");
        }
    }
}

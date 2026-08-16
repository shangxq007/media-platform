package com.example.platform.timeline.canonicalmodel;

import java.util.List;
import java.util.Objects;

public record TimelineTrackCanonical(
        String trackId,
        Type type,
        int zOrder,
        Double audioGain,
        List<TimelineClipCanonical> clips) {

    public TimelineTrackCanonical {
        trackId = TimelineCanonicalModel.requireNormalizedIdentifier(trackId, "trackId");
        type = Objects.requireNonNull(type, "type");
        clips = List.copyOf(Objects.requireNonNull(clips, "clips"));
    }

    public enum Type {
        VIDEO,
        AUDIO
    }
}

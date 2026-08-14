package com.example.platform.render.domain.timeline.canonicalmodel;

import com.example.platform.shared.time.MediaTime;
import java.util.Objects;

public record TimelineClipCanonical(
        String clipId,
        TimelineSourceRef sourceRef,
        MediaTime timelineStart,
        MediaTime sourceStart,
        MediaTime duration) {

    public TimelineClipCanonical {
        clipId = TimelineCanonicalModel.requireNormalizedIdentifier(clipId, "clipId");
        sourceRef = Objects.requireNonNull(sourceRef, "sourceRef");
        timelineStart = Objects.requireNonNull(timelineStart, "timelineStart");
        sourceStart = Objects.requireNonNull(sourceStart, "sourceStart");
        duration = Objects.requireNonNull(duration, "duration");
    }

    public MediaTime timelineEnd() {
        return timelineStart.add(duration);
    }

    public MediaTime sourceEnd() {
        return sourceStart.add(duration);
    }
}

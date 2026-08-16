package com.example.platform.timeline.canonicalmodel;

import com.example.platform.shared.time.MediaTime;
import java.util.List;
import java.util.Objects;

public record TimelineCanonicalModel(
        String timelineId,
        String projectId,
        TimelineCanonicalProfile profile,
        List<TimelineTrackCanonical> tracks,
        MediaTime duration) {

    public TimelineCanonicalModel {
        timelineId = requireNormalizedIdentifier(timelineId, "timelineId");
        projectId = projectId == null ? null : requireNormalizedIdentifier(projectId, "projectId");
        profile = Objects.requireNonNull(profile, "profile");
        tracks = List.copyOf(Objects.requireNonNull(tracks, "tracks"));
        duration = Objects.requireNonNull(duration, "duration");
    }

    static String requireNormalizedIdentifier(String value, String label) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(label + " must be nonblank and already normalized");
        }
        return value;
    }
}

package com.example.platform.render.domain.timeline.canonicalmodel;

import com.example.platform.render.domain.timeline.semantics.time.MediaTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TimelineCanonicalNormalizer {
    static final Comparator<TimelineCandidate.Track> TRACK_CANDIDATE_ORDERING = Comparator
            .comparingInt(TimelineCandidate.Track::zOrder)
            .thenComparing(track -> track.type() == TimelineCandidate.TrackType.VIDEO ? 0 : 1)
            .thenComparing(TimelineCandidate.Track::trackId, Comparator.nullsLast(String::compareTo));

    static final Comparator<TimelineCandidate.Clip> CLIP_CANDIDATE_ORDERING = Comparator
            .comparing(TimelineCandidate.Clip::timelineStart, Comparator.nullsLast(MediaTime::compareTo))
            .thenComparing(TimelineCandidate.Clip::clipId, Comparator.nullsLast(String::compareTo));

    private TimelineCanonicalNormalizer() {
    }

    public static Optional<TimelineCanonicalModel> normalize(TimelineCandidate candidate) {
        TimelineValidationResult validation = TimelineCanonicalValidator.validate(candidate);
        if (validation.hasFatalErrors()) {
            return Optional.empty();
        }
        List<TimelineTrackCanonical> tracks = candidate.tracks().stream()
                .sorted(TRACK_CANDIDATE_ORDERING)
                .map(TimelineCanonicalNormalizer::normalizeTrack)
                .toList();
        MediaTime duration = tracks.stream()
                .flatMap(track -> track.clips().stream())
                .map(TimelineClipCanonical::timelineEnd)
                .reduce(MediaTime.ZERO, MediaTime::max);
        return Optional.of(new TimelineCanonicalModel(candidate.timelineId(), candidate.projectId(),
                candidate.profile() == null ? TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1 : candidate.profile(),
                tracks, duration));
    }

    private static TimelineTrackCanonical normalizeTrack(TimelineCandidate.Track track) {
        List<TimelineClipCanonical> clips = track.clips().stream()
                .sorted(CLIP_CANDIDATE_ORDERING)
                .map(TimelineCanonicalNormalizer::normalizeClip)
                .toList();
        return new TimelineTrackCanonical(track.trackId(), TimelineTrackCanonical.Type.valueOf(track.type().name()),
                track.zOrder(), defaultGain(track), clips);
    }

    private static TimelineClipCanonical normalizeClip(TimelineCandidate.Clip clip) {
        return new TimelineClipCanonical(clip.clipId(), Objects.requireNonNull(clip.sourceRef(), "sourceRef"),
                clip.timelineStart(), clip.sourceStart(), clip.duration());
    }

    private static Double defaultGain(TimelineCandidate.Track track) {
        if (track.type() == TimelineCandidate.TrackType.AUDIO) {
            return track.audioGain() == null ? 1.0d : track.audioGain();
        }
        return track.audioGain();
    }
}

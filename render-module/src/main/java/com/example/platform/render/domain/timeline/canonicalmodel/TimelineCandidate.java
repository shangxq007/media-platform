package com.example.platform.render.domain.timeline.canonicalmodel;

import com.example.platform.render.domain.timeline.semantics.time.MediaTime;
import java.util.List;
import java.util.Objects;

public record TimelineCandidate(
        String timelineId,
        String projectId,
        TimelineCanonicalProfile profile,
        List<Track> tracks) {

    public TimelineCandidate {
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
    }

    public static TimelineCandidate of(String timelineId, String projectId, TimelineCanonicalProfile profile,
            List<Track> tracks) {
        return new TimelineCandidate(timelineId, projectId, profile, tracks);
    }

    public static TimelineCandidate fromCanonical(TimelineCanonicalModel model) {
        Objects.requireNonNull(model, "model");
        return new TimelineCandidate(model.timelineId(), model.projectId(), model.profile(),
                model.tracks().stream()
                        .map(track -> new Track(track.trackId(), TrackType.valueOf(track.type().name()),
                                track.zOrder(), track.audioGain(),
                                track.clips().stream()
                                        .map(clip -> new Clip(clip.clipId(), clip.sourceRef(), clip.timelineStart(),
                                                clip.sourceStart(), clip.duration(), null))
                                        .toList()))
                        .toList());
    }

    public static Track track(String trackId, TrackType type, int zOrder, Double audioGain, List<Clip> clips) {
        return new Track(trackId, type, zOrder, audioGain, clips);
    }

    public static Clip clip(String clipId, TimelineSourceRef sourceRef, MediaTime timelineStart,
            MediaTime sourceStart, MediaTime duration, List<Object> unsupportedConstructs) {
        return new Clip(clipId, sourceRef, timelineStart, sourceStart, duration, unsupportedConstructs);
    }

    public enum TrackType {
        VIDEO,
        AUDIO,
        SUBTITLE
    }

    public record Track(String trackId, TrackType type, int zOrder, Double audioGain, List<Clip> clips) {
        public Track {
            clips = clips == null ? List.of() : List.copyOf(clips);
        }
    }

    public record Clip(String clipId, TimelineSourceRef sourceRef, MediaTime timelineStart,
            MediaTime sourceStart, MediaTime duration, List<Object> unsupportedConstructs) {
        public Clip {
            unsupportedConstructs = unsupportedConstructs == null ? List.of() : List.copyOf(unsupportedConstructs);
        }
    }
}

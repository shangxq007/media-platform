package com.example.platform.timeline.canonicalmodel;

import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import java.util.List;
import java.util.Objects;

public record TimelineCandidate(
        String timelineId,
        String projectId,
        TimelineCanonicalProfile profile,
        List<Track> tracks,
        List<CanonicalTransition> transitions,
        List<CanonicalAutomationCurve> automations) {

    public TimelineCandidate {
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
        automations = automations == null ? List.of() : List.copyOf(automations);
    }

    /** Convenience constructor without transitions/automations (structural only). */
    public TimelineCandidate(
            String timelineId,
            String projectId,
            TimelineCanonicalProfile profile,
            List<Track> tracks) {
        this(timelineId, projectId, profile, tracks, List.of(), List.of());
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
                                                clip.sourceStart(), clip.duration(), FrameRate.of(30, 1),
                                                List.of(), null))
                                        .toList()))
                        .toList(),
                model.transitions(), model.automations());
    }

    public static Track track(String trackId, TrackType type, int zOrder, Double audioGain, List<Clip> clips) {
        return new Track(trackId, type, zOrder, audioGain, clips);
    }

    public static Clip clip(String clipId, TimelineSourceRef sourceRef, MediaTime timelineStart,
            MediaTime sourceStart, MediaTime duration, List<Object> unsupportedConstructs) {
        return new Clip(clipId, sourceRef, timelineStart, sourceStart, duration,
                FrameRate.of(30, 1), List.of(), unsupportedConstructs);
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
            MediaTime sourceStart, MediaTime duration, FrameRate rate,
            List<TimelineClipEffect> effects, List<Object> unsupportedConstructs) {
        public Clip {
            unsupportedConstructs = unsupportedConstructs == null ? List.of() : List.copyOf(unsupportedConstructs);
            effects = effects == null ? List.of() : List.copyOf(effects);
        }
    }
}

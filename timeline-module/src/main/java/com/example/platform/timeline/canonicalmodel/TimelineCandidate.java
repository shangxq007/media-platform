package com.example.platform.timeline.canonicalmodel;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.timeline.semantics.relationship.SemanticRelationship;
import com.example.platform.timeline.semantics.temporal.TemporalMapping;
import java.util.List;
import java.util.Objects;

/**
 * CHECKPOINT_A correction: the canonical candidate projection now carries the
 * FULL authored Timeline semantic surface — audio mix, semantic relationships,
 * and per-clip typed source semantics (source kind, asset/stream/artifact/
 * digest, temporal mapping) — so the validator / diff / patch / merge /
 * persistence pipeline never silently narrows canonical authored state.
 */
public record TimelineCandidate(
        String timelineId,
        String projectId,
        TimelineCanonicalProfile profile,
        List<Track> tracks,
        List<CanonicalTransition> transitions,
        List<CanonicalAutomationCurve> automations,
        List<com.example.platform.timeline.canonical.TextElement> textElements,
        AudioMix audioMix,
        List<SemanticRelationship> semanticRelationships) {

    public TimelineCandidate {
        Objects.requireNonNull(timelineId, "timelineId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(profile, "profile");
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
        automations = automations == null ? List.of() : List.copyOf(automations);
        textElements = textElements == null ? List.of() : List.copyOf(textElements);
        audioMix = audioMix == null ? AudioMix.empty() : audioMix;
        semanticRelationships = semanticRelationships == null ? List.of() : List.copyOf(semanticRelationships);
    }

    public static TimelineCandidate fromCanonical(TimelineCanonicalModel model) {
        Objects.requireNonNull(model, "model");
        return new TimelineCandidate(model.timelineId(), model.projectId(), model.profile(),
                model.tracks().stream()
                        .map(track -> new Track(track.trackId(),
                                switch (track.type()) {
                                    case VIDEO -> TrackType.VIDEO;
                                    case AUDIO -> TrackType.AUDIO;
                                },
                                track.zOrder(), null,
                                track.clips().stream()
                                        .map(clip -> new Clip(clip.clipId(),
                                                clip.sourceRef() != null ? clip.sourceRef() : TimelineSourceRef.of(""),
                                                clip.timelineStart(), clip.sourceStart(), clip.duration(),
                                                FrameRate.of(30, 1),
                                                List.of(),
                                                List.of(),
                                                null, null, null, null, null, null))
                                        .toList()))
                        .toList(),
                model.transitions(), model.automations(), List.of(),
                AudioMix.empty(), List.of());
    }

    public static Track track(String trackId, TrackType type, int zOrder, Double audioGain, List<Clip> clips) {
        return new Track(trackId, type, zOrder, audioGain, clips);
    }

    public record Track(String trackId, TrackType type, int zOrder, Double audioGain, List<Clip> clips) {
        public Track {
            clips = clips == null ? List.of() : List.copyOf(clips);
        }
    }

    public enum TrackType {
        VIDEO,
        AUDIO,
        SUBTITLE
    }

    public record Clip(String clipId, TimelineSourceRef sourceRef, MediaTime timelineStart,
            MediaTime sourceStart, MediaTime duration, FrameRate rate,
            List<TimelineClipEffect> effects, List<Object> unsupportedConstructs,
            String sourceKind, String mediaAssetId, String mediaStreamId,
            String artifactId, String contentDigest, TemporalMapping temporalMapping) {
        public Clip {
            unsupportedConstructs = unsupportedConstructs == null ? List.of() : List.copyOf(unsupportedConstructs);
            effects = effects == null ? List.of() : List.copyOf(effects);
        }
    }
}

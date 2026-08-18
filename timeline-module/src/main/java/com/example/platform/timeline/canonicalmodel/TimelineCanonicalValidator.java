package com.example.platform.timeline.canonicalmodel;

import com.example.platform.shared.time.MediaTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TimelineCanonicalValidator {
    private TimelineCanonicalValidator() {
    }

    public static TimelineValidationResult validate(TimelineCandidate candidate) {
        List<TimelineDiagnostic> diagnostics = new ArrayList<>();
        if (candidate == null) {
            diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_ROOT_NULL, TimelineModelPath.root(), null,
                    "Timeline candidate must not be null"));
            return TimelineValidationResult.of(diagnostics);
        }

        validateIdentifier(candidate.timelineId(), TimelineDiagnosticCode.TIMELINE_ID_MISSING,
                TimelineModelPath.root().field("timelineId"), candidate.timelineId(), diagnostics);

        Map<String, Integer> trackCounts = new HashMap<>();
        Map<String, Integer> clipCounts = new HashMap<>();
        Map<String, TimelineCandidate.Clip> clipsById = new HashMap<>();
        for (TimelineCandidate.Track track : candidate.tracks()) {
            if (track.trackId() != null) {
                trackCounts.merge(track.trackId(), 1, Integer::sum);
            }
            for (TimelineCandidate.Clip clip : track.clips()) {
                if (clip.clipId() != null) {
                    clipCounts.merge(clip.clipId(), 1, Integer::sum);
                    clipsById.put(clip.clipId(), clip);
                }
            }
        }

        // FOURTH CORRECTION — aggregate reference validation (BLOCKER 1):
        // every Transition endpoint must reference an existing Clip in the
        // SAME aggregate and must not be a self-reference; every Automation
        // target must reference an existing authored semantic entity
        // (Effect instance within the aggregate).
        validateTransitionReferences(candidate, clipsById, diagnostics);
        validateAutomationTargets(candidate, clipsById, diagnostics);
        trackCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_TRACK_ID_DUPLICATE,
                        TimelineModelPath.root().field("tracks"), entry.getKey(), "Duplicate track identifier")));
        clipCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_CLIP_ID_DUPLICATE,
                        TimelineModelPath.root().field("tracks").id(findTrackIdForClip(candidate, entry.getKey())).field("clips"),
                        entry.getKey(), "Duplicate clip identifier")));

        for (int trackIndex = 0; trackIndex < candidate.tracks().size(); trackIndex++) {
            TimelineCandidate.Track track = candidate.tracks().get(trackIndex);
            TimelineModelPath trackPath = TimelineModelPath.root().field("tracks");
            if (track.trackId() == null || track.trackId().isBlank()) {
                trackPath = trackPath.index(trackIndex);
            } else {
                trackPath = trackPath.id(track.trackId());
            }
            validateTrack(track, trackPath, diagnostics);
        }

        return TimelineValidationResult.of(diagnostics);
    }

    private static void validateTrack(TimelineCandidate.Track track, TimelineModelPath trackPath,
            List<TimelineDiagnostic> diagnostics) {
        validateIdentifier(track.trackId(), TimelineDiagnosticCode.TIMELINE_TRACK_ID_MISSING,
                trackPath.field("trackId"), track.trackId(), diagnostics);
        if (track.type() == null || track.type() == TimelineCandidate.TrackType.SUBTITLE) {
            diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_TRACK_TYPE_UNSUPPORTED,
                    trackPath.field("type"), track.trackId(), "Unsupported track type"));
        }
        if (track.audioGain() != null && (track.audioGain().isNaN() || track.audioGain() < 0.0d)) {
            diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_AUDIO_GAIN_INVALID,
                    trackPath.field("audioGain"), track.trackId(), "Audio gain must be nonnegative"));
        }
        List<TimelineCandidate.Clip> ordered = track.clips().stream()
                .sorted(TimelineCanonicalNormalizer.CLIP_CANDIDATE_ORDERING)
                .toList();
        TimelineCandidate.Clip previous = null;
        for (int clipIndex = 0; clipIndex < ordered.size(); clipIndex++) {
            TimelineCandidate.Clip clip = ordered.get(clipIndex);
            TimelineModelPath clipPath = clip.clipId() == null || clip.clipId().isBlank()
                    ? trackPath.field("clips").index(clipIndex)
                    : trackPath.field("clips").id(clip.clipId());
            validateClip(clip, clipPath, diagnostics);
            if (previous != null && hasPositiveOverlap(previous, clip)) {
                diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_CLIP_OVERLAP,
                        clipPath, clip.clipId(), "Clips overlap on the same track"));
            }
            previous = clip;
        }
    }

    private static void validateClip(TimelineCandidate.Clip clip, TimelineModelPath clipPath,
            List<TimelineDiagnostic> diagnostics) {
        validateIdentifier(clip.clipId(), TimelineDiagnosticCode.TIMELINE_CLIP_ID_MISSING,
                clipPath.field("clipId"), clip.clipId(), diagnostics);
        if (clip.sourceRef() == null) {
            diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_SOURCE_REF_MISSING,
                    clipPath.field("sourceRef"), clip.clipId(), "Source reference is required"));
        }
        validateTime(clip.timelineStart(), TimelineDiagnosticCode.TIMELINE_TIME_NEGATIVE,
                clipPath.field("timelineStart"), clip.clipId(), diagnostics);
        validateTime(clip.sourceStart(), TimelineDiagnosticCode.TIMELINE_TIME_NEGATIVE,
                clipPath.field("sourceStart"), clip.clipId(), diagnostics);
        validateDuration(clip.duration(), clipPath.field("duration"), clip.clipId(), diagnostics);
        if (clip.unsupportedConstructs() != null && !clip.unsupportedConstructs().isEmpty()) {
            diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_CONSTRUCT_UNSUPPORTED,
                    clipPath.field("unsupportedConstructs"), clip.clipId(), "Unsupported Timeline construct"));
        }
    }

    private static void validateIdentifier(String value, TimelineDiagnosticCode code, TimelineModelPath path,
            String relatedIdentifier, List<TimelineDiagnostic> diagnostics) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            diagnostics.add(error(code, path, relatedIdentifier, "Identifier must be nonblank and already normalized"));
        }
    }

    private static void validateTime(MediaTime value, TimelineDiagnosticCode code, TimelineModelPath path,
            String relatedIdentifier, List<TimelineDiagnostic> diagnostics) {
        if (value == null) {
            diagnostics.add(error(code, path, relatedIdentifier, "MediaTime is required"));
        }
    }

    private static void validateDuration(MediaTime value, TimelineModelPath path, String relatedIdentifier,
            List<TimelineDiagnostic> diagnostics) {
        if (value == null) {
            diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_DURATION_NEGATIVE, path, relatedIdentifier,
                    "Duration is required"));
        } else if (value.equals(MediaTime.ZERO)) {
            diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_DURATION_ZERO, path, relatedIdentifier,
                    "Duration must be greater than zero"));
        }
    }

    private static boolean hasPositiveOverlap(TimelineCandidate.Clip previous, TimelineCandidate.Clip current) {
        if (previous.timelineStart() == null || previous.duration() == null || current.timelineStart() == null) {
            return false;
        }
        try {
            return previous.timelineStart().add(previous.duration()).isGreaterThan(current.timelineStart());
        } catch (ArithmeticException ex) {
            return false;
        }
    }

    private static String findTrackIdForClip(TimelineCandidate candidate, String clipId) {
        return candidate.tracks().stream()
                .filter(track -> track.clips().stream().anyMatch(clip -> clipId.equals(clip.clipId())))
                .map(TimelineCandidate.Track::trackId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElse("unknown");
    }

    private static TimelineDiagnostic error(TimelineDiagnosticCode code, TimelineModelPath path,
            String relatedIdentifier, String message) {
        return TimelineDiagnostic.error(code, path, relatedIdentifier, message);
    }

    /**
     * FOURTH CORRECTION (BLOCKER 1): aggregate transition endpoint validation.
     * Every transition endpoint must reference an existing Clip in the SAME
     * aggregate; outgoing != incoming (no self-reference).
     */
    private static void validateTransitionReferences(TimelineCandidate candidate,
            Map<String, TimelineCandidate.Clip> clipsById, List<TimelineDiagnostic> diagnostics) {
        if (candidate.transitions() == null) {
            return;
        }
        for (int i = 0; i < candidate.transitions().size(); i++) {
            CanonicalTransition t = candidate.transitions().get(i);
            TimelineModelPath path = TimelineModelPath.root().field("transitions").index(i);
            String outgoing = t.outgoingClipId();
            String incoming = t.incomingClipId();
            if (outgoing == null || outgoing.isBlank() || !clipsById.containsKey(outgoing)) {
                diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_TRANSITION_ENDPOINT_MISSING,
                        path.field("outgoingClipId"), outgoing == null ? "<null>" : outgoing,
                        "Transition outgoingClipId must reference an existing Clip in the same timeline"));
            }
            if (incoming == null || incoming.isBlank() || !clipsById.containsKey(incoming)) {
                diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_TRANSITION_ENDPOINT_MISSING,
                        path.field("incomingClipId"), incoming == null ? "<null>" : incoming,
                        "Transition incomingClipId must reference an existing Clip in the same timeline"));
            }
            if (outgoing != null && outgoing.equals(incoming)) {
                diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_TRANSITION_SELF_REFERENCE,
                        path, t.transitionId(),
                        "Transition outgoingClipId and incomingClipId must differ"));
            }
        }
    }

    /**
     * FOURTH CORRECTION (BLOCKER 1): aggregate automation target validation.
     * The supported target universe (from repository reality) is an Effect
     * instance within the same aggregate; the referenced Effect identity must
     * exist on some Clip of the timeline.
     */
    private static void validateAutomationTargets(TimelineCandidate candidate,
            Map<String, TimelineCandidate.Clip> clipsById, List<TimelineDiagnostic> diagnostics) {
        if (candidate.automations() == null || clipsById.isEmpty()) {
            return;
        }
        java.util.Set<String> effectIds = new java.util.HashSet<>();
        for (TimelineCandidate.Clip clip : clipsById.values()) {
            if (clip.effects() != null) {
                for (TimelineClipEffect e : clip.effects()) {
                    if (e.id() != null) {
                        effectIds.add(e.id());
                    }
                }
            }
        }
        for (int i = 0; i < candidate.automations().size(); i++) {
            CanonicalAutomationCurve a = candidate.automations().get(i);
            String target = a.targetEntityId();
            if (target == null || target.isBlank() || !effectIds.contains(target)) {
                diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_AUTOMATION_TARGET_MISSING,
                        TimelineModelPath.root().field("automations").index(i).field("targetEntityId"),
                        target == null ? "<null>" : target,
                        "Automation targetEntityId must reference an existing Effect instance in the same timeline"));
            }
        }
    }
}

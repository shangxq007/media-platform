package com.example.platform.timeline.canonicalmodel;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.semantics.effect.BasicVideoEffect;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
        validateTransitions(candidate, clipsById, diagnostics);
        // FIFTH CORRECTION (F1.2): Effect instance identity is an aggregate
        // invariant — duplicate non-null Effect IDs fail closed REGARDLESS of
        // whether any Automation references them.
        validateEffectIdUniqueness(candidate, clipsById, diagnostics);
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
            validateBasicVideoEffect(track, clip, clipPath, diagnostics);
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

    private static void validateTransitions(TimelineCandidate candidate,
            Map<String, TimelineCandidate.Clip> clipsById, List<TimelineDiagnostic> diagnostics) {
        if (candidate.transitions().isEmpty()) return;
        var layers = new HashMap<String, Integer>();
        for (int i = 0; i < candidate.tracks().size(); i++) {
            for (var clip : candidate.tracks().get(i).clips()) {
                layers.put(clip.clipId(), i);
            }
        }
        var ids = new HashSet<String>();
        var incomingEndpoints = new HashSet<String>();
        var windows = new ArrayList<DissolveWindow>();
        for (int i = 0; i < candidate.transitions().size(); i++) {
            CanonicalTransition transition = candidate.transitions().get(i);
            TimelineModelPath path = TimelineModelPath.root().field("transitions").index(i);
            var outgoing = clipsById.get(transition.outgoingClipId());
            var incoming = clipsById.get(transition.incomingClipId());
            if (!ids.add(transition.transitionId())) {
                diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_TRANSITION_CONFLICT,
                        path, transition.transitionId(), "Duplicate transition identity"));
            }
            if (outgoing == null || incoming == null) {
                String missing = outgoing == null ? "outgoingClipId" : "incomingClipId";
                diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_TRANSITION_ENDPOINT_MISSING,
                        path.field(missing), transition.transitionId(), "Transition must reference existing clips"));
                continue;
            }
            if (outgoing == incoming) {
                diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_TRANSITION_SELF_REFERENCE,
                        path, transition.transitionId(), "Transition endpoints must be different"));
                continue;
            }
            if (!transition.isCrossDissolve()) continue;
            try {
                validateDissolve(candidate, layers, transition, outgoing, incoming,
                        incomingEndpoints, windows, path, diagnostics);
            } catch (ArithmeticException invalid) {
                diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_TRANSITION_INTERVAL_INVALID,
                        path, transition.transitionId(), "Transition time is outside the representable range"));
            }
        }
    }

    private static void validateDissolve(TimelineCandidate candidate, Map<String, Integer> layers,
            CanonicalTransition transition, TimelineCandidate.Clip outgoing, TimelineCandidate.Clip incoming,
            java.util.Set<String> incomingEndpoints, List<DissolveWindow> windows,
            TimelineModelPath path, List<TimelineDiagnostic> diagnostics) {
        String id = transition.transitionId();
        int lower = layers.get(outgoing.clipId());
        int upper = layers.get(incoming.clipId());
        if (!transition.supportsCrossDissolve() || lower >= upper
                || candidate.tracks().get(lower).type() != TimelineCandidate.TrackType.VIDEO
                || candidate.tracks().get(upper).type() != TimelineCandidate.TrackType.VIDEO) {
            diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_TRANSITION_PROFILE_UNSUPPORTED,
                    path, id, "Cross dissolve requires the R1 profile and an incoming clip on a higher video layer"));
            return;
        }
        if (!ConstantRateTemporalMapping.identity().equals(outgoing.temporalMapping())
                || !ConstantRateTemporalMapping.identity().equals(incoming.temporalMapping())) {
            diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_TRANSITION_MAPPING_UNSUPPORTED,
                    path, id, "Cross dissolve requires forward 1x endpoint mappings"));
        }
        if (!incomingEndpoints.add(incoming.clipId())) {
            diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_TRANSITION_CONFLICT,
                    path, id, "More than one transition targets the same incoming clip"));
        }
        if (incoming.timelineStart() == null || outgoing.timelineStart() == null
                || incoming.duration() == null || outgoing.duration() == null) return;
        MediaTime start = incoming.timelineStart();
        MediaTime end = start.add(transition.duration());
        if (start.isLessThan(outgoing.timelineStart())
                || end.isGreaterThan(outgoing.timelineStart().add(outgoing.duration()))
                || end.isGreaterThan(start.add(incoming.duration()))) {
            diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_TRANSITION_INTERVAL_INVALID,
                    path, id, "Cross dissolve interval must fit within both clips"));
            return;
        }
        for (int layer = lower + 1; layer < upper; layer++) {
            var track = candidate.tracks().get(layer);
            if (track.type() == TimelineCandidate.TrackType.VIDEO && track.clips().stream().anyMatch(clip ->
                    clip.timelineStart() != null && clip.duration() != null
                    && overlaps(start, end, clip.timelineStart(), clip.timelineStart().add(clip.duration())))) {
                diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_TRANSITION_CONFLICT,
                        path, id, "An intervening video clip covers the authored outgoing layer during the dissolve"));
                break;
            }
        }
        var window = new DissolveWindow(outgoing.clipId(), incoming.clipId(), start, end);
        if (windows.stream().anyMatch(previous -> previous.conflictsWith(window))) {
            diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_TRANSITION_CONFLICT,
                    path, id, "Transitions sharing a clip cannot overlap"));
        }
        windows.add(window);
    }

    private static boolean overlaps(MediaTime start, MediaTime end, MediaTime otherStart, MediaTime otherEnd) {
        return start.isLessThan(otherEnd) && otherStart.isLessThan(end);
    }

    private record DissolveWindow(String outgoing, String incoming, MediaTime start, MediaTime end) {
        boolean conflictsWith(DissolveWindow other) {
            return (outgoing.equals(other.outgoing) || outgoing.equals(other.incoming)
                    || incoming.equals(other.outgoing) || incoming.equals(other.incoming))
                    && overlaps(start, end, other.start, other.end);
        }
    }

    private static void validateBasicVideoEffect(TimelineCandidate.Track track, TimelineCandidate.Clip clip,
            TimelineModelPath path, List<TimelineDiagnostic> diagnostics) {
        boolean found = false;
        for (var effect : clip.effects()) {
            if (!BasicVideoEffect.EFFECT_KEY.equals(effect.effectKey())) continue;
            if (track.type() != TimelineCandidate.TrackType.VIDEO) {
                diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_EFFECT_TARGET_UNSUPPORTED,
                        path.field("effects"), clip.clipId(), "video.basic requires a clip on a video track"));
            }
            try {
                if (found) throw new IllegalArgumentException("Only one video.basic effect is supported per clip");
                var basic = BasicVideoEffect.fromParameters(effect.parameters());
                if (basic.enabled() && clip.duration() != null) {
                    var duration = clip.duration();
                    var longestFade = BigDecimal.valueOf(Math.max(basic.fadeInSeconds(), basic.fadeOutSeconds()));
                    if (longestFade.multiply(BigDecimal.valueOf(duration.timeScale()))
                            .compareTo(BigDecimal.valueOf(duration.ticks())) > 0) {
                        throw new IllegalArgumentException("video.basic fade exceeds its authored clip interval");
                    }
                }
            } catch (IllegalArgumentException invalid) {
                diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_EFFECT_PARAMETERS_INVALID,
                        path.field("effects"), clip.clipId(), invalid.getMessage()));
            }
            found = true;
        }
    }

    /**
     * FIFTH CORRECTION (F1.2): Effect instance identity aggregate invariant.
     * Non-null Effect IDs must be unique across the whole Timeline — duplicate
     * IDs create ambiguous Automation references and fail closed.
     */
    private static void validateEffectIdUniqueness(TimelineCandidate candidate,
            Map<String, TimelineCandidate.Clip> clipsById, List<TimelineDiagnostic> diagnostics) {
        java.util.Set<String> effectIds = new java.util.HashSet<>();
        for (TimelineCandidate.Clip clip : clipsById.values()) {
            if (clip.effects() != null) {
                for (TimelineClipEffect e : clip.effects()) {
                    if (e.id() != null && !e.id().isBlank() && !effectIds.add(e.id())) {
                        diagnostics.add(error(TimelineDiagnosticCode.TIMELINE_EFFECT_ID_DUPLICATE,
                                TimelineModelPath.root().field("tracks"), e.id(),
                                "Duplicate Effect instance id across the timeline — ambiguous reference"));
                    }
                }
            }
        }
    }

    /**
     * FIFTH CORRECTION (BLOCKER F1): aggregate automation target validation.
     * The supported target universe (from repository reality) is an Effect
     * instance within the same aggregate; the referenced Effect identity must
     * exist exactly once (Effect IDs are aggregate-unique — duplicate non-null
     * Effect IDs create ambiguous references and fail closed). A Timeline with
     * zero Clips but automations MUST NOT bypass target validation.
     */
    private static void validateAutomationTargets(TimelineCandidate candidate,
            Map<String, TimelineCandidate.Clip> clipsById, List<TimelineDiagnostic> diagnostics) {
        if (candidate.automations() == null || candidate.automations().isEmpty()) {
            return;
        }
        java.util.Set<String> effectIds = new java.util.HashSet<>();
        for (TimelineCandidate.Clip clip : clipsById.values()) {
            if (clip.effects() != null) {
                for (TimelineClipEffect e : clip.effects()) {
                    if (e.id() != null && !e.id().isBlank()) {
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

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
        for (TimelineCandidate.Track track : candidate.tracks()) {
            if (track.trackId() != null) {
                trackCounts.merge(track.trackId(), 1, Integer::sum);
            }
            for (TimelineCandidate.Clip clip : track.clips()) {
                if (clip.clipId() != null) {
                    clipCounts.merge(clip.clipId(), 1, Integer::sum);
                }
            }
        }
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
}

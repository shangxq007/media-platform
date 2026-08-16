package com.example.platform.timeline.canonicalmodel;

import com.example.platform.shared.time.MediaTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimelineCanonicalValidatorTest {

    @Test
    void emptyTimelineIsValidAndNormalizesToEmptyModel() {
        TimelineCandidate candidate = TimelineCandidate.of("timeline-empty", "project-1",
                TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1, List.of());

        TimelineValidationResult result = TimelineCanonicalValidator.validate(candidate);
        TimelineCanonicalModel model = TimelineCanonicalNormalizer.normalize(candidate).orElseThrow();

        assertFalse(result.hasFatalErrors());
        assertTrue(result.diagnostics().isEmpty());
        assertTrue(model.tracks().isEmpty());
        assertEquals(MediaTime.ZERO, model.duration());
    }

    @Test
    void duplicateTrackIdsAndDuplicateClipIdsAreFatalWithinTimelineScope() {
        TimelineCandidate candidate = TimelineCandidate.of("timeline-1", "project-1",
                TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1,
                List.of(track("track", clip("clip", "source-a", 0, 5)),
                        track("track", clip("clip", "source-b", 5, 5))));

        assertEquals(List.of(TimelineDiagnosticCode.TIMELINE_TRACK_ID_DUPLICATE,
                        TimelineDiagnosticCode.TIMELINE_CLIP_ID_DUPLICATE),
                TimelineCanonicalValidator.validate(candidate).diagnostics().stream().map(TimelineDiagnostic::code).toList());
    }

    @Test
    void invalidTimingAndSourceRangeProduceFrozenDiagnostics() {
        TimelineCandidate zero = TimelineCandidate.of("timeline-1", "project-1",
                TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1,
                List.of(track("track", TimelineCandidate.clip("clip", TimelineSourceRef.of("source-a"),
                        MediaTime.ZERO, MediaTime.ZERO, MediaTime.ZERO, null))));
        TimelineCandidate missingSource = TimelineCandidate.of("timeline-1", "project-1",
                TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1,
                List.of(track("track", TimelineCandidate.clip("clip", null,
                        MediaTime.ZERO, MediaTime.ZERO, MediaTime.ofTicks(1, 1), null))));

        assertEquals(TimelineDiagnosticCode.TIMELINE_DURATION_ZERO,
                TimelineCanonicalValidator.validate(zero).diagnostics().getFirst().code());
        assertEquals(TimelineDiagnosticCode.TIMELINE_SOURCE_REF_MISSING,
                TimelineCanonicalValidator.validate(missingSource).diagnostics().getFirst().code());
    }

    @Test
    void unsupportedConstructAndUnsupportedTrackTypeAreFatal() {
        TimelineCandidate candidate = TimelineCandidate.of("timeline-1", "project-1",
                TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1,
                List.of(TimelineCandidate.track("subtitle", TimelineCandidate.TrackType.SUBTITLE, 0, null,
                        List.of(TimelineCandidate.clip("clip", TimelineSourceRef.of("source-a"),
                                MediaTime.ZERO, MediaTime.ZERO, MediaTime.ofTicks(1, 1), List.of("effect"))))));

        assertEquals(List.of(TimelineDiagnosticCode.TIMELINE_TRACK_TYPE_UNSUPPORTED,
                        TimelineDiagnosticCode.TIMELINE_CONSTRUCT_UNSUPPORTED),
                TimelineCanonicalValidator.validate(candidate).diagnostics().stream().map(TimelineDiagnostic::code).toList());
    }

    private static TimelineCandidate.Track track(String id, TimelineCandidate.Clip... clips) {
        return TimelineCandidate.track(id, TimelineCandidate.TrackType.VIDEO, 0, null, List.of(clips));
    }

    private static TimelineCandidate.Clip clip(String id, String sourceId, long start, long duration) {
        return TimelineCandidate.clip(id, TimelineSourceRef.of(sourceId), MediaTime.ofTicks(start, 1),
                MediaTime.ZERO, MediaTime.ofTicks(duration, 1), null);
    }
}

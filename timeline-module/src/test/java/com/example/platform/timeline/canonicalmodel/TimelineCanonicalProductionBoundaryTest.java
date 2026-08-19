package com.example.platform.timeline.canonicalmodel;

import com.example.platform.shared.time.MediaTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimelineCanonicalProductionBoundaryTest {

    @Test
    void validCandidateUsesProductionValidationAndNormalizationBoundary() {
        TimelineCandidate candidate = new TimelineCandidate(
                "timeline-1",
                "project-1",
                TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1,
                List.of(
                        TimelineCandidate.track("video-a", TimelineCandidate.TrackType.VIDEO, 2, null,
                                List.of(new TimelineCandidate.Clip("clip-b", TimelineSourceRef.of("raw-b"), MediaTime.ofTicks(5, 1), MediaTime.ZERO, MediaTime.ofTicks(5, 1), com.example.platform.shared.time.FrameRate.of(30, 1), java.util.List.of(), null, null, null))),
                        TimelineCandidate.track("video-b", TimelineCandidate.TrackType.VIDEO, 1, null,
                                List.of(new TimelineCandidate.Clip("clip-a", TimelineSourceRef.of("raw-a"), MediaTime.ZERO, MediaTime.ZERO, MediaTime.ofTicks(5, 1), com.example.platform.shared.time.FrameRate.of(30, 1), java.util.List.of(), null, null, null)))), List.of(), List.of(), List.of(), com.example.platform.audio.domain.mix.AudioMix.empty(), java.util.List.of());

        TimelineValidationResult validation = TimelineCanonicalValidator.validate(candidate);
        TimelineCanonicalModel normalized = TimelineCanonicalNormalizer.normalize(candidate).orElseThrow();
        TimelineCanonicalModel repeated = TimelineCanonicalNormalizer.normalize(candidate).orElseThrow();
        TimelineCanonicalModel idempotent = TimelineCanonicalNormalizer.normalize(TimelineCandidate.fromCanonical(normalized)).orElseThrow();

        assertFalse(validation.hasFatalErrors());
        assertTrue(validation.diagnostics().isEmpty());
        assertEquals(List.of("video-b", "video-a"), normalized.tracks().stream().map(TimelineTrackCanonical::trackId).toList());
        assertEquals("clip-a", normalized.tracks().get(0).clips().get(0).clipId());
        assertEquals(MediaTime.ofTicks(10, 1), normalized.duration());
        assertEquals(normalized, repeated);
        assertEquals(normalized, idempotent);
        assertThrows(UnsupportedOperationException.class, () -> normalized.tracks().add(normalized.tracks().getFirst()));
    }

    @Test
    void invalidCandidateReturnsStableOrderedDiagnosticsAndNoCanonicalModel() {
        TimelineCandidate candidate = new TimelineCandidate(
                "timeline-1",
                "project-1",
                TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1,
                List.of(
                        TimelineCandidate.track("video", TimelineCandidate.TrackType.VIDEO, 0, null,
                                List.of(new TimelineCandidate.Clip("clip-1", TimelineSourceRef.of("raw-a"), MediaTime.ZERO, MediaTime.ZERO, MediaTime.ofTicks(5, 1), com.example.platform.shared.time.FrameRate.of(30, 1), java.util.List.of(), null, null, null),
                                        new TimelineCandidate.Clip("clip-1", TimelineSourceRef.of("raw-b"), MediaTime.ofTicks(3, 1), MediaTime.ofTicks(2, 1), MediaTime.ofTicks(2, 1), com.example.platform.shared.time.FrameRate.of(30, 1), java.util.List.of(), null, null, null))),
                        TimelineCandidate.track("video", TimelineCandidate.TrackType.SUBTITLE, 0, null, List.of())), List.of(), List.of(), List.of(), com.example.platform.audio.domain.mix.AudioMix.empty(), java.util.List.of());

        TimelineValidationResult validation = TimelineCanonicalValidator.validate(candidate);

        assertTrue(validation.hasFatalErrors());
        assertEquals(List.of(
                        TimelineDiagnosticCode.TIMELINE_TRACK_ID_DUPLICATE,
                        TimelineDiagnosticCode.TIMELINE_TRACK_TYPE_UNSUPPORTED,
                        TimelineDiagnosticCode.TIMELINE_CLIP_ID_DUPLICATE,
                        TimelineDiagnosticCode.TIMELINE_CLIP_OVERLAP),
                validation.diagnostics().stream().map(TimelineDiagnostic::code).toList());
        assertEquals(List.of(
                        "$.tracks",
                        "$.tracks[id=video].type",
                        "$.tracks[id=video].clips",
                        "$.tracks[id=video].clips[id=clip-1]"),
                validation.diagnostics().stream().map(d -> d.path().render()).toList());
        assertTrue(TimelineCanonicalNormalizer.normalize(candidate).isEmpty());
    }
}

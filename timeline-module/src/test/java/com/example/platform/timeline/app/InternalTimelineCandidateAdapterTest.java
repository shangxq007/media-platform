package com.example.platform.timeline.app;

import com.example.platform.timeline.app.InternalTimelineCandidateAdapter;import com.example.platform.timeline.app.TimelineCanonicalRejectionException;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalProfile;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalValidator;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract G adapter unit tests (PTCSG_RECORD_REVISION_CANONICAL_GATE_E1B_V1).
 * Proves the frozen internal-1.0 -> TimelineCandidate mapping: exact rational timing
 * (no floating point), the five frozen rejection codes, determinism, and purity.
 */
class InternalTimelineCandidateAdapterTest {

    private static final String VALID = """
            {"schemaVersion":"1.0","id":"tl-1",
             "composition":{"tracks":[
               {"id":"v1","type":"VIDEO","clips":[
                 {"id":"c1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}},
                  "sourceRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}}]}]}}""";

    private static TimelineCanonicalRejectionException.Code rejectionCode(String json) {
        TimelineCanonicalRejectionException ex = assertThrows(TimelineCanonicalRejectionException.class,
                () -> InternalTimelineCandidateAdapter.map("prj-1", json));
        assertFalse(ex.adapterDiagnostics().isEmpty(), "adapter rejection must carry an adapter diagnostic");
        return ex.adapterDiagnostics().get(0).code();
    }

    @Test
    void validInternalTimeline_mapsCompletely() {
        TimelineCandidate candidate = InternalTimelineCandidateAdapter.map("prj-1", VALID);
        assertEquals("tl-1", candidate.timelineId());
        assertEquals("prj-1", candidate.projectId());
        assertEquals(TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1, candidate.profile());
        assertEquals(1, candidate.tracks().size());
        assertEquals("v1", candidate.tracks().get(0).trackId());
        assertEquals(TimelineCandidate.TrackType.VIDEO, candidate.tracks().get(0).type());
        assertEquals(0, candidate.tracks().get(0).zOrder());
        assertNull(candidate.tracks().get(0).audioGain());
        TimelineCandidate.Clip clip = candidate.tracks().get(0).clips().get(0);
        assertEquals("c1", clip.clipId());
        assertEquals("ast-1", clip.sourceRef().value());
        assertEquals(MediaTime.ZERO, clip.timelineStart());
        assertEquals(MediaTime.ofRational(1, 1), clip.duration(), "30 frames @ 30fps must be exactly 1 second");
        // Valid candidate passes the canonical validator.
        assertFalse(TimelineCanonicalValidator.validate(candidate).hasFatalErrors());
    }

    @Test
    void exactRationalTiming_noFloatingPoint() {
        String json = """
                {"schemaVersion":"1.0","id":"tl-2",
                 "composition":{"tracks":[
                   {"id":"v1","type":"VIDEO","clips":[
                     {"id":"c1","assetId":"ast-1",
                      "timelineRange":{"start":{"frame":1,"rate":{"num":25,"den":1}},"duration":{"frame":1,"rate":{"num":25,"den":1}}}}]}]}}""";
        TimelineCandidate candidate = InternalTimelineCandidateAdapter.map("prj-1", json);
        TimelineCandidate.Clip clip = candidate.tracks().get(0).clips().get(0);
        // 1 frame @ 25fps = 1/25 s, expressed exactly (no floating point).
        assertEquals(MediaTime.ofRational(1, 25), clip.timelineStart());
        assertEquals(MediaTime.ofRational(1, 25), clip.duration());
    }

    @Test
    void duplicateTrackIdentifiers_areCanonicallyFatal() {
        String json = """
                {"schemaVersion":"1.0","id":"tl-dup",
                 "composition":{"tracks":[
                   {"id":"dup","type":"VIDEO","clips":[]},
                   {"id":"dup","type":"AUDIO","clips":[]}]}}""";
        TimelineCandidate candidate = InternalTimelineCandidateAdapter.map("prj-1", json);
        assertTrue(TimelineCanonicalValidator.validate(candidate).hasFatalErrors(),
                "duplicate track identifiers must be canonically fatal");
    }

    @Test
    void unsupportedSchema_rejectedWithFrozenCode() {
        assertEquals(TimelineCanonicalRejectionException.Code.TIMELINE_SCHEMA_UNSUPPORTED,
                rejectionCode("{\"schemaVersion\":\"9.9\",\"id\":\"x\"}"));
    }

    @Test
    void malformedJson_rejectedWithFrozenCode() {
        assertEquals(TimelineCanonicalRejectionException.Code.TIMELINE_SCHEMA_UNSUPPORTED,
                rejectionCode("{not-json"));
    }

    @Test
    void missingComposition_rejectedWithFrozenCode() {
        assertEquals(TimelineCanonicalRejectionException.Code.TIMELINE_SCHEMA_UNSUPPORTED,
                rejectionCode("{\"schemaVersion\":\"1.0\",\"id\":\"x\"}"));
    }

    @Test
    void blankTimelineId_rejectedWithFrozenCode() {
        assertEquals(TimelineCanonicalRejectionException.Code.TIMELINE_CLIP_ID_INVALID,
                rejectionCode("{\"schemaVersion\":\"1.0\",\"id\":\"  \",\"composition\":{\"tracks\":[]}}"));
    }

    @Test
    void blankTrackId_rejectedWithFrozenCode() {
        assertEquals(TimelineCanonicalRejectionException.Code.TIMELINE_CLIP_ID_INVALID,
                rejectionCode("""
                        {"schemaVersion":"1.0","id":"tl-x",
                         "composition":{"tracks":[{"id":"","type":"VIDEO","clips":[]}]}}"""));
    }

    @Test
    void unsupportedTrackType_rejectedWithFrozenCode() {
        assertEquals(TimelineCanonicalRejectionException.Code.TIMELINE_TRACK_TYPE_UNSUPPORTED,
                rejectionCode("""
                        {"schemaVersion":"1.0","id":"tl-x",
                         "composition":{"tracks":[{"id":"v1","type":"EFFECT","clips":[]}]}}"""));
    }

    @Test
    void blankAssetId_rejectedWithFrozenCode() {
        assertEquals(TimelineCanonicalRejectionException.Code.TIMELINE_SOURCE_REF_INVALID,
                rejectionCode("""
                        {"schemaVersion":"1.0","id":"tl-x",
                         "composition":{"tracks":[{"id":"v1","type":"VIDEO","clips":[
                           {"id":"c1","assetId":" ",
                            "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}}]}]}}"""));
    }

    @Test
    void zeroDuration_rejectedWithFrozenCode() {
        assertEquals(TimelineCanonicalRejectionException.Code.TIMELINE_TIMING_INVALID,
                rejectionCode("""
                        {"schemaVersion":"1.0","id":"tl-x",
                         "composition":{"tracks":[{"id":"v1","type":"VIDEO","clips":[
                           {"id":"c1","assetId":"ast-1",
                            "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":0,"rate":{"num":30,"den":1}}}}]}]}}"""));
    }

    @Test
    void mapping_isDeterministicAndPure() {
        TimelineCandidate a = InternalTimelineCandidateAdapter.map("prj-1", VALID);
        TimelineCandidate b = InternalTimelineCandidateAdapter.map("prj-1", VALID);
        assertEquals(a, b);
    }

    @Test
    void additionalAdapterCodes_zero() {
        // FIFTH CORRECTION (F4.1): +TIMELINE_EFFECT_KEY_INVALID — blank
        // effectKey now fails closed at the adapter (no opaque fallback).
        assertEquals(6, TimelineCanonicalRejectionException.Code.values().length,
                "the six frozen adapter codes must remain exactly six (F4.1 added TIMELINE_EFFECT_KEY_INVALID)");
    }
}

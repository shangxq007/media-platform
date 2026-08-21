package com.example.platform.timeline.app;

import com.example.platform.timeline.app.InternalTimelineCandidateAdapter;import com.example.platform.timeline.app.InternalTimelineJson;import com.example.platform.timeline.app.TimelineRevisionService;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalValidator;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineSnapshot;
import com.example.platform.timeline.diff.calculation.TimelineSnapshotConverter;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C1-CRR1 permanent regression — canonical payload contract reconciliation.
 *
 * <p>Replaces the blocked R1 diagnostic probe with PASSING assertions proving
 * the contradiction is resolved for the canonical PERSISTED payload shape
 * (internal-1.0, as previously authored by the legacy recordRevision path);
 * persists it):</p>
 *
 * <pre>
 *   productionSavedPayloadIsAcceptedByCanonicalGate         = PASS
 *   productionSavedPayloadCanBeConvertedToCanonicalMergeSnapshot = PASS
 *   validation-accepted domain == merge-conversion domain   = PASS (no ∅)
 * </pre>
 *
 * <p>The payload below is the production save format (schemaVersion "1.0",
 * composition block, frame-based ranges @30fps) — the same shape as the E1b
 * gate integration fixtures used by the canonical save path.</p>
 */
class TimelineMergePayloadContractRegressionTest {

    private static final String PROJECT = "proj-1";
    private static final String REVISION = "rev-1";

    private static final String PRODUCTION_SAVE_PAYLOAD = """
            {"schemaVersion":"1.0","id":"tl-prod",
             "composition":{"tracks":[
               {"id":"v1","type":"VIDEO","clips":[
                 {"id":"c1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":90,"rate":{"num":30,"den":1}}},
                  "sourceRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":90,"rate":{"num":30,"den":1}}}}]}]}}
            """;

    @Test
    void productionSavedPayloadIsAcceptedByCanonicalGate() {
        TimelineCandidate candidate = InternalTimelineCandidateAdapter.map(PROJECT, PRODUCTION_SAVE_PAYLOAD);
        assertNotNull(candidate);
        assertFalse(TimelineCanonicalValidator.validate(candidate).hasFatalErrors(),
                "production saved payload must pass canonical validation");
        assertEquals("tl-prod", candidate.timelineId());
        assertEquals(1, candidate.tracks().size());
        assertEquals("v1", candidate.tracks().get(0).trackId());
        assertEquals("c1", candidate.tracks().get(0).clips().get(0).clipId());
    }

    @Test
    void productionSavedPayloadCanBeConvertedToCanonicalMergeSnapshot() {
        TimelineCandidate candidate = InternalTimelineCandidateAdapter.map(PROJECT, PRODUCTION_SAVE_PAYLOAD);
        CanonicalTimelineSnapshot snapshot =
                TimelineSnapshotConverter.toSnapshot(candidate, REVISION);
        assertNotNull(snapshot);
        assertEquals(REVISION, snapshot.revisionId());
        assertEquals(1, snapshot.tracks().size());
        assertEquals("v1", snapshot.tracks().get(0).trackId());
        assertEquals("c1", snapshot.tracks().get(0).clips().get(0).clipId());
        assertEquals("ast-1", snapshot.tracks().get(0).clips().get(0).assetBindingId());
        // C1-CNM1: exact MediaTime snapshot; 90 frames @30fps = 3000ms.
        // start frame 0 -> MediaTime.ZERO; duration 90 frames @30/1.
        assertEquals(MediaTime.ZERO, snapshot.tracks().get(0).clips().get(0).start());
        assertEquals(MediaTime.ofFrames(90, 30, 1),
                snapshot.tracks().get(0).clips().get(0).duration());
        assertEquals(FrameRate.of(30, 1), snapshot.tracks().get(0).clips().get(0).rate());
    }

    @Test
    void validationAndConversionDomainsAreEqual() throws Exception {
        // The payload accepted by the gate is the SAME payload consumed by the
        // merge conversion — no disjoint accepted-domain (the C1 contradiction
        // was: accepted(gate) ∩ accepted(parse) = ∅).
        JsonNode root = InternalTimelineJson.parse(PRODUCTION_SAVE_PAYLOAD);
        assertTrue(InternalTimelineJson.isInternalTimeline(root),
                "gate domain: production persisted payload is internal-1.0");
        TimelineCandidate candidate = InternalTimelineCandidateAdapter.map(PROJECT, PRODUCTION_SAVE_PAYLOAD);
        CanonicalTimelineSnapshot snapshot = TimelineSnapshotConverter.toSnapshot(candidate, REVISION);
        assertNotNull(snapshot);
        assertTrue(snapshot.tracks().stream().anyMatch(t -> t.clips().stream()
                        .anyMatch(c -> c.clipId().equals("c1"))),
                "conversion domain: same payload yields a merge snapshot");
    }

    @Test
    void timelineDocumentShapeIsNotPersistenceAuthority() throws Exception {
        // TimelineDocument (schemaVersion "timeline-1.0", tracks) is the
        // patch/restore serialization shape; it is NOT the canonical persisted
        // revision payload consumed by production merge. Assert the canonical
        // persisted authority is internal-1.0 (composition) and NOT
        // "timeline-1.0"-labelled.
        assertTrue(InternalTimelineJson.isInternalTimeline(
                InternalTimelineJson.parse(PRODUCTION_SAVE_PAYLOAD)));
        assertEquals("1.0", InternalTimelineJson.schemaVersion(
                InternalTimelineJson.parse(PRODUCTION_SAVE_PAYLOAD)));
    }
}

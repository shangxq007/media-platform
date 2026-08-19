package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.timeline.semantics.clip.TimelineSourceBinding;
import com.example.platform.timeline.semantics.clip.TimelineSourceBindingCanonicalSemantics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

/**
 * POST_FINAL_REVIEW_P2: strict source-binding boundary closure.
 *
 * P2-A: PRESENT-but-malformed nested sourceBinding must FAIL CLOSED (never
 * silently narrowed to absence). ABSENT field → null allowed.
 *
 * P2-B: TimelineDocument binding intent requires the EXACT authored source
 * range — missing trimStart/trimEnd must NOT be synthesized to 0..0.
 */
class CheckpointAPostFinalReviewSourceBindingBoundaryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DIGEST_HEX = "ab".repeat(32);

    // ── P2-A: nested sourceBinding strict boundary (adapter level) ─────────

    private static ObjectNode clipNode(JsonNode sourceBindingField) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("schemaVersion", "1.0");
        root.put("id", "tl-1");
        ObjectNode composition = MAPPER.createObjectNode();
        com.fasterxml.jackson.databind.node.ArrayNode tracks = MAPPER.createArrayNode();
        ObjectNode track = MAPPER.createObjectNode();
        track.put("id", "v1");
        track.put("type", "VIDEO");
        com.fasterxml.jackson.databind.node.ArrayNode clips = MAPPER.createArrayNode();
        ObjectNode clip = MAPPER.createObjectNode();
        clip.put("id", "c1");
        clip.put("assetId", "ast-1");
        ObjectNode timelineRange = MAPPER.createObjectNode();
        ObjectNode tlStart = MAPPER.createObjectNode();
        tlStart.put("frame", 0);
        ObjectNode tlRate = MAPPER.createObjectNode();
        tlRate.put("num", 30);
        tlRate.put("den", 1);
        tlStart.set("rate", tlRate);
        timelineRange.set("start", tlStart);
        ObjectNode tlDuration = MAPPER.createObjectNode();
        tlDuration.put("frame", 30);
        tlDuration.set("rate", tlRate.deepCopy());
        timelineRange.set("duration", tlDuration);
        clip.set("timelineRange", timelineRange);
        if (sourceBindingField != null) {
            clip.set("sourceBinding", sourceBindingField);
        }
        clips.add(clip);
        track.set("clips", clips);
        tracks.add(track);
        composition.set("tracks", tracks);
        root.set("composition", composition);
        return root;
    }

    private static void assertRejected(JsonNode sbField) {
        assertThrows(com.example.platform.timeline.app.TimelineCanonicalRejectionException.class,
                () -> InternalTimelineCandidateAdapter.map("proj-1", clipNode(sbField).toString()));
    }

    @Test
    void sourceBindingFieldAbsentAllowsNullBinding() {
        com.example.platform.timeline.canonicalmodel.TimelineCandidate candidate =
                InternalTimelineCandidateAdapter.map("proj-1", clipNode(null).toString());
        assertNull(candidate.tracks().get(0).clips().get(0).sourceBinding(),
                "fully ABSENT sourceBinding allows null binding");
    }

    @Test
    void sourceBindingNullFailsClosed() {
        assertRejected(MAPPER.nullNode());
    }

    @Test
    void sourceBindingEmptyStringFailsClosed() {
        assertRejected(MAPPER.getNodeFactory().textNode(""));
    }

    @Test
    void sourceBindingGarbageStringFailsClosed() {
        assertRejected(MAPPER.getNodeFactory().textNode("garbage"));
    }

    @Test
    void sourceBindingArrayFailsClosed() {
        assertRejected(MAPPER.createArrayNode());
    }

    @Test
    void sourceBindingBooleanFailsClosed() {
        assertRejected(MAPPER.getNodeFactory().booleanNode(true));
    }

    @Test
    void sourceBindingNumberFailsClosed() {
        assertRejected(MAPPER.getNodeFactory().numberNode(123));
    }

    @Test
    void sourceBindingEmptyObjectFailsClosed() {
        assertRejected(MAPPER.createObjectNode());
    }

    @Test
    void nestedObjectMissingSourceKindFailsClosed() {
        ObjectNode sb = MAPPER.createObjectNode();
        sb.put("mediaAssetId", "ast-1");
        sb.put("mediaStreamId", "stream-1");
        sb.put("artifactId", "art-1");
        ObjectNode digest = sb.putObject("contentDigest");
        digest.put("algorithm", "SHA_256");
        digest.put("value", DIGEST_HEX);
        sb.put("sourceRangeStart", "0/1");
        sb.put("sourceRangeEnd", "1/1");
        assertRejected(sb);
    }

    @Test
    void nestedObjectMissingDigestFailsClosed() {
        ObjectNode sb = MAPPER.createObjectNode();
        sb.put("sourceKind", "MEDIA_STREAM");
        sb.put("mediaAssetId", "ast-1");
        sb.put("mediaStreamId", "stream-1");
        sb.put("artifactId", "art-1");
        sb.put("sourceRangeStart", "0/1");
        sb.put("sourceRangeEnd", "1/1");
        assertRejected(sb);
    }

    @Test
    void nestedObjectMalformedRangeFailsClosed() {
        ObjectNode sb = MAPPER.createObjectNode();
        sb.put("sourceKind", "MEDIA_STREAM");
        sb.put("mediaAssetId", "ast-1");
        sb.put("mediaStreamId", "stream-1");
        sb.put("artifactId", "art-1");
        ObjectNode digest = sb.putObject("contentDigest");
        digest.put("algorithm", "SHA_256");
        digest.put("value", DIGEST_HEX);
        // start > end
        sb.put("sourceRangeStart", "5/1");
        sb.put("sourceRangeEnd", "1/1");
        assertRejected(sb);
    }

    // ── canonical decoder root strictness (G32) ────────────────────────────

    @Test
    void canonicalDecoderRejectsMalformedRoot() {
        assertThrows(IllegalStateException.class,
                () -> TimelineSourceBindingCanonicalSemantics.fromCanonicalValue(MAPPER.nullNode()));
        assertThrows(IllegalStateException.class,
                () -> TimelineSourceBindingCanonicalSemantics.fromCanonicalValue(MAPPER.createObjectNode()));
        assertThrows(IllegalStateException.class,
                () -> TimelineSourceBindingCanonicalSemantics.fromCanonicalValue(
                        MAPPER.getNodeFactory().textNode("garbage")));
        // null Java reference still means caller-level absence
        assertNull(TimelineSourceBindingCanonicalSemantics.fromCanonicalValue(null));
    }

    // ── P2-B: TimelineDocument missing exact source range ──────────────────

    private static TimelineClip clipWith(String trimStart, String trimEnd, boolean withIntent) {
        return new TimelineClip(
                "c1",
                withIntent ? "ast-1" : "ast-1",
                withIntent ? "stream-1" : null,
                withIntent ? "art-1" : null,
                withIntent ? DIGEST_HEX : null,
                MediaTime.ZERO, MediaTime.ofTicks(30, 1),
                trimStart != null ? MediaTime.parse(trimStart) : null,
                trimEnd != null ? MediaTime.parse(trimEnd) : null,
                withIntent ? "MEDIA_STREAM" : null,
                null);
    }

    private static TimelineDocument doc(TimelineClip clip) {
        return new TimelineDocument(
                com.example.platform.timeline.canonical.TimelineDocument.CURRENT_SCHEMA_VERSION,
                java.util.List.of(new com.example.platform.timeline.canonical.TimelineTrack(
                        "v1", "video", com.example.platform.timeline.canonical.TrackType.VIDEO,
                        java.util.List.of(clip))),
                com.example.platform.timeline.canonical.TimelineMetadata.empty());
    }

    private static void assertDocumentRejected(TimelineClip clip) {
        assertThrows(com.example.platform.timeline.app.TimelineCanonicalRejectionException.class,
                () -> TimelineDocumentCandidateMapper.map("proj-1", doc(clip)));
    }

    @Test
    void bindingIntentMissingTrimStartFailsClosed() {
        assertDocumentRejected(clipWith(null, "1/1", true));
    }

    @Test
    void bindingIntentMissingTrimEndFailsClosed() {
        assertDocumentRejected(clipWith("0/1", null, true));
    }

    @Test
    void bindingIntentMissingBothRangeValuesFailsClosed() {
        assertDocumentRejected(clipWith(null, null, true));
    }

    @Test
    void bindingIntentStartGreaterThanEndFailsClosed() {
        assertDocumentRejected(clipWith("5/1", "1/1", true));
    }

    @Test
    void noBindingIntentAbsentRangeAllowed() {
        // no binding-specific fields → no intent → null binding is allowed
        com.example.platform.timeline.canonicalmodel.TimelineCandidate candidate =
                TimelineDocumentCandidateMapper.map("proj-1", doc(clipWith(null, null, false)));
        assertNull(candidate.tracks().get(0).clips().get(0).sourceBinding());
    }

    @Test
    void explicitValidExactRangeRoundtrips() {
        TimelineClip clip = clipWith("0/1", "1/1", true);
        com.example.platform.timeline.canonicalmodel.TimelineCandidate candidate =
                TimelineDocumentCandidateMapper.map("proj-1", doc(clip));
        TimelineSourceBinding binding = candidate.tracks().get(0).clips().get(0).sourceBinding();
        assertNotNull(binding);
        assertTrue(binding instanceof MediaStreamSourceBinding);
        MediaStreamSourceBinding msb = (MediaStreamSourceBinding) binding;
        assertEquals(MediaTime.parse("0/1"), msb.sourceRange().start());
        assertEquals(MediaTime.parse("1/1"), msb.sourceRange().end());
        assertEquals(new ArtifactId("art-1"), msb.artifactId());
        assertEquals(new MediaStreamId("stream-1"), msb.mediaStreamId());
        assertEquals(new MediaAssetId("ast-1"), msb.mediaAssetId());
        assertEquals(DIGEST_HEX, msb.contentDigest().value());
        assertEquals(ContentDigest.DigestAlgorithm.SHA_256, msb.contentDigest().algorithm());
    }

    @Test
    void explicitAuthoredZeroRangePreserved() {
        // Authored 0..0 is DISTINCT from missing — it must survive as 0..0.
        TimelineClip clip = clipWith("0/1", "0/1", true);
        com.example.platform.timeline.canonicalmodel.TimelineCandidate candidate =
                TimelineDocumentCandidateMapper.map("proj-1", doc(clip));
        TimelineSourceBinding binding = candidate.tracks().get(0).clips().get(0).sourceBinding();
        assertNotNull(binding);
        MediaStreamSourceBinding msb = (MediaStreamSourceBinding) binding;
        assertEquals(MediaTime.ZERO, msb.sourceRange().start());
        assertEquals(MediaTime.ZERO, msb.sourceRange().end());
    }
}

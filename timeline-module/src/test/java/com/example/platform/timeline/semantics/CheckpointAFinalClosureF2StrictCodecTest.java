package com.example.platform.timeline.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.semantics.clip.TimelineSourceBinding;
import com.example.platform.timeline.semantics.clip.TimelineSourceBindingCanonicalSemantics;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.transition.TransitionCanonicalSemantics;
import com.example.platform.timeline.semantics.automation.AutomationCanonicalSemantics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

/**
 * FINAL_CLOSURE_F2 (post-Round-5): strict canonical codec tests.
 *
 * 1-8   contentDigest.algorithm required / unknown fails closed / roundtrip
 * 9-18  flat sourceRange required fields, no default synthesis, exact rational
 * 19-21 Transition ticks/timeScale integral JSON nodes
 * 22-25 Automation keyframe numeric JSON strictness
 */
class CheckpointAFinalClosureF2StrictCodecTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DIGEST_HEX = "ab".repeat(32);
    private static final String OTHER_HEX = "cd".repeat(32);

    // ── helpers ────────────────────────────────────────────────────────────

    private static ObjectNode bindingNode(String algorithm, String value) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("sourceKind", "MEDIA_STREAM");
        node.put("mediaAssetId", "ast-1");
        node.put("mediaStreamId", "stream-1");
        node.put("artifactId", "art-1");
        ObjectNode digest = node.putObject("contentDigest");
        digest.put("algorithm", algorithm);
        digest.put("value", value);
        node.put("sourceRangeStart", "0/1");
        node.put("sourceRangeEnd", "1/1");
        return node;
    }

    private static MediaStreamSourceBinding streamBinding() {
        return new MediaStreamSourceBinding(
                new MediaAssetId("ast-1"),
                new MediaStreamId("stream-1"),
                new ArtifactId("art-1"),
                new ContentDigest(ContentDigest.DigestAlgorithm.SHA_256, DIGEST_HEX),
                new MediaClip.TimeRange(
                        MediaTime.parse("0/1"), MediaTime.parse("1/1")));
    }

    private static ObjectNode transitionNode(JsonNode durationTicks, JsonNode durationTimeScale) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("transitionDefinitionId", "def-1");
        node.put("transitionDefinitionVersion", "1");
        node.put("outgoingClipId", "clip-a");
        node.put("incomingClipId", "clip-b");
        node.put("mediaType", "VIDEO");
        node.set("durationTicks", durationTicks);
        node.set("durationTimeScale", durationTimeScale);
        node.put("alignment", "CENTER_ON_CUT");
        node.put("temporalPolicy", "USE_SOURCE_HANDLES");
        return node;
    }

    private static JsonNode num(long v) {
        return MAPPER.getNodeFactory().numberNode(v);
    }

    private static JsonNode numD(double v) {
        return MAPPER.getNodeFactory().numberNode(v);
    }

    private static JsonNode text(String v) {
        return MAPPER.getNodeFactory().textNode(v);
    }

    private static ObjectNode automationKeyframeNode(
            JsonNode timeTicks, JsonNode timeTimeScale, JsonNode value) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("keyframeId", "kf-1");
        node.set("timeTicks", timeTicks);
        node.set("timeTimeScale", timeTimeScale);
        node.set("value", value);
        node.put("interpolation", "LINEAR");
        return node;
    }

    // ── 1-8: contentDigest.algorithm ──────────────────────────────────────

    @Test
    void digestCanonicalSha256AlgorithmRoundtripsExactly() {
        MediaStreamSourceBinding decoded = (MediaStreamSourceBinding)
                TimelineSourceBindingCanonicalSemantics.fromCanonicalValue(
                        bindingNode("SHA_256", DIGEST_HEX));
        assertEquals(ContentDigest.DigestAlgorithm.SHA_256,
                decoded.contentDigest().algorithm(), "canonical algorithm decoded");
        assertEquals(DIGEST_HEX, decoded.contentDigest().value(), "exact digest value");
    }

    @Test
    void digestMissingAlgorithmFailsClosed() {
        ObjectNode node = bindingNode("SHA_256", DIGEST_HEX);
        ((ObjectNode) node.path("contentDigest")).remove("algorithm");
        assertThrows(IllegalStateException.class,
                () -> TimelineSourceBindingCanonicalSemantics.fromCanonicalValue(node));
    }

    @Test
    void digestUnknownAlgorithmFailsClosed() {
        assertThrows(IllegalStateException.class,
                () -> TimelineSourceBindingCanonicalSemantics.fromCanonicalValue(
                        bindingNode("MD5", DIGEST_HEX)));
        assertThrows(IllegalStateException.class,
                () -> TimelineSourceBindingCanonicalSemantics.fromCanonicalValue(
                        bindingNode("sha-256", DIGEST_HEX)));
    }

    @Test
    void digestLegacySha256AliasFailsClosedInCanonicalDecoder() {
        // The canonical decoder must NOT silently normalize the legacy wire
        // alias "SHA256" — only the adapter boundary may map it (covered by
        // InternalTimelineCandidateAdapter tests). Canonical = "SHA_256".
        assertThrows(IllegalStateException.class,
                () -> TimelineSourceBindingCanonicalSemantics.fromCanonicalValue(
                        bindingNode("SHA256", DIGEST_HEX)));
    }

    @Test
    void digestMissingValueFailsClosed() {
        ObjectNode node = bindingNode("SHA_256", DIGEST_HEX);
        ((ObjectNode) node.path("contentDigest")).remove("value");
        assertThrows(IllegalStateException.class,
                () -> TimelineSourceBindingCanonicalSemantics.fromCanonicalValue(node));
    }

    @Test
    void digestMalformedValueFailsClosed() {
        assertThrows(RuntimeException.class,
                () -> TimelineSourceBindingCanonicalSemantics.fromCanonicalValue(
                        bindingNode("SHA_256", "not-a-hex-digest")));
        assertThrows(RuntimeException.class,
                () -> TimelineSourceBindingCanonicalSemantics.fromCanonicalValue(
                        bindingNode("SHA_256", "")));
    }

    @Test
    void digestContentDigestNotObjectFailsClosed() {
        ObjectNode node = bindingNode("SHA_256", DIGEST_HEX);
        node.set("contentDigest", text("SHA256:" + DIGEST_HEX));
        assertThrows(IllegalStateException.class,
                () -> TimelineSourceBindingCanonicalSemantics.fromCanonicalValue(node));
    }

    @Test
    void canonicalValuePreservesAlgorithmExactly() {
        MediaStreamSourceBinding binding = streamBinding();
        JsonNode canonical = TimelineSourceBindingCanonicalSemantics.canonicalValue(binding);
        assertEquals("SHA_256", canonical.path("contentDigest").path("algorithm").asText());
        // canonicalValue → fromCanonicalValue → exact same binding
        TimelineSourceBinding decoded =
                TimelineSourceBindingCanonicalSemantics.fromCanonicalValue(canonical);
        assertTrue(decoded instanceof MediaStreamSourceBinding);
        MediaStreamSourceBinding msb = (MediaStreamSourceBinding) decoded;
        assertEquals(binding.contentDigest().algorithm(), msb.contentDigest().algorithm());
        assertEquals(binding.contentDigest().value(), msb.contentDigest().value());
        assertEquals(binding.sourceRange().start(), msb.sourceRange().start());
        assertEquals(binding.sourceRange().end(), msb.sourceRange().end());
        assertEquals(binding.mediaAssetId(), msb.mediaAssetId());
        assertEquals(binding.mediaStreamId(), msb.mediaStreamId());
        assertEquals(binding.artifactId(), msb.artifactId());
    }

    // ── 9-18: flat sourceRange strict decode ──────────────────────────────

    private static JsonNode flatRange(JsonNode num, JsonNode den, JsonNode startFrame, JsonNode durationFrame) {
        ObjectNode range = MAPPER.createObjectNode();
        ObjectNode start = MAPPER.createObjectNode();
        ObjectNode rate = MAPPER.createObjectNode();
        rate.set("num", num);
        rate.set("den", den);
        start.set("rate", rate);
        start.set("frame", startFrame);
        ObjectNode duration = MAPPER.createObjectNode();
        duration.set("frame", durationFrame);
        range.set("start", start);
        range.set("duration", duration);
        return range;
    }

    private static TimelineSourceBinding flatBinding(JsonNode range) {
        return TimelineSourceBindingCanonicalSemantics.fromFlatFields(
                "MEDIA_STREAM", "ast-1", "stream-1", "art-1", DIGEST_HEX, range);
    }

    @Test
    void flatRangeMissingRateNumFailsClosed() {
        ObjectNode range = (ObjectNode) flatRange(num(30), num(1), num(0), num(30));
        ((ObjectNode) range.path("start").path("rate")).remove("num");
        assertThrows(IllegalStateException.class, () -> flatBinding(range));
    }

    @Test
    void flatRangeMissingRateDenFailsClosed() {
        ObjectNode range = (ObjectNode) flatRange(num(30), num(1), num(0), num(30));
        ((ObjectNode) range.path("start").path("rate")).remove("den");
        assertThrows(IllegalStateException.class, () -> flatBinding(range));
    }

    @Test
    void flatRangeZeroRateNumFailsClosed() {
        assertThrows(IllegalStateException.class,
                () -> flatBinding(flatRange(num(0), num(1), num(0), num(30))));
    }

    @Test
    void flatRangeZeroRateDenFailsClosed() {
        assertThrows(IllegalStateException.class,
                () -> flatBinding(flatRange(num(30), num(0), num(0), num(30))));
    }

    @Test
    void flatRangeMissingStartFrameFailsClosed() {
        ObjectNode range = (ObjectNode) flatRange(num(30), num(1), num(0), num(30));
        ((ObjectNode) range.path("start")).remove("frame");
        assertThrows(IllegalStateException.class, () -> flatBinding(range));
    }

    @Test
    void flatRangeMissingDurationFrameFailsClosed() {
        ObjectNode range = (ObjectNode) flatRange(num(30), num(1), num(0), num(30));
        ((ObjectNode) range.path("duration")).remove("frame");
        assertThrows(IllegalStateException.class, () -> flatBinding(range));
    }

    @Test
    void flatRangeStringFrameFailsClosed() {
        assertThrows(IllegalStateException.class,
                () -> flatBinding(flatRange(num(30), num(1), text("0"), num(30))));
        assertThrows(IllegalStateException.class,
                () -> flatBinding(flatRange(num(30), num(1), num(0), text("30"))));
    }

    @Test
    void flatRangeStringRateFailsClosed() {
        assertThrows(IllegalStateException.class,
                () -> flatBinding(flatRange(text("30"), num(1), num(0), num(30))));
        assertThrows(IllegalStateException.class,
                () -> flatBinding(flatRange(num(30), text("1"), num(0), num(30))));
    }

    @Test
    void flatRangeMalformedObjectFailsClosed() {
        assertThrows(IllegalStateException.class,
                () -> flatBinding(text("not-an-object")));
        assertThrows(IllegalStateException.class,
                () -> flatBinding(MAPPER.createObjectNode()));
        // missing duration object entirely
        ObjectNode range = (ObjectNode) flatRange(num(30), num(1), num(0), num(30));
        range.remove("duration");
        assertThrows(IllegalStateException.class, () -> flatBinding(range));
    }

    @Test
    void flatRangeValidExactTypedRange() {
        TimelineSourceBinding decoded = flatBinding(
                flatRange(num(30), num(1), num(0), num(30)));
        assertTrue(decoded instanceof MediaStreamSourceBinding);
        MediaStreamSourceBinding msb = (MediaStreamSourceBinding) decoded;
        // frame 0 @ 30/1 → 0s; duration 30 frames @ 30/1 → 1s → end = 1s
        assertEquals(MediaTime.parse("0/1"), msb.sourceRange().start());
        assertEquals(MediaTime.parse("1/1"), msb.sourceRange().end());
        // exact rational with non-unit den: frame 1 @ 30/1 → 1/30 s
        TimelineSourceBinding exact = flatBinding(
                flatRange(num(30), num(1), num(1), num(30)));
        assertEquals(MediaTime.ofTicks(1, 30),
                ((MediaStreamSourceBinding) exact).sourceRange().start());
    }

    // ── 19-21: Transition numeric JSON strictness ─────────────────────────

    @Test
    void transitionTicksStringFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCanonicalSemantics.fromCanonicalValue("t-1",
                        transitionNode(text("15"), num(30))));
    }

    @Test
    void transitionTimeScaleStringFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCanonicalSemantics.fromCanonicalValue("t-1",
                        transitionNode(num(15), text("30"))));
    }

    @Test
    void transitionNonIntegralDurationFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCanonicalSemantics.fromCanonicalValue("t-1",
                        transitionNode(MAPPER.getNodeFactory().booleanNode(true), num(30))));
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCanonicalSemantics.fromCanonicalValue("t-1",
                        transitionNode(MAPPER.createObjectNode(), num(30))));
    }

    // ── 22-25: Automation numeric JSON strictness ─────────────────────────

    private static JsonNode automationNode(JsonNode kfNode) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("targetEntityId", "clip-1");
        node.put("parameterPath", "scale.x");
        node.put("valueType", "float");
        node.put("extrapolation", "HOLD");
        node.set("keyframes", kfNode);
        return node;
    }

    @Test
    void automationTimeTicksStringFailsClosed() {
        com.fasterxml.jackson.databind.node.ArrayNode arr = MAPPER.createArrayNode();
        arr.add(automationKeyframeNode(text("10"), num(30), numD(0.5)));
        assertThrows(IllegalArgumentException.class,
                () -> AutomationCanonicalSemantics.fromCanonicalValue("a-1", automationNode(arr)));
    }

    @Test
    void automationTimeTimeScaleStringFailsClosed() {
        com.fasterxml.jackson.databind.node.ArrayNode arr = MAPPER.createArrayNode();
        arr.add(automationKeyframeNode(num(10), text("30"), numD(0.5)));
        assertThrows(IllegalArgumentException.class,
                () -> AutomationCanonicalSemantics.fromCanonicalValue("a-1", automationNode(arr)));
    }

    @Test
    void automationValueNonNumberFailsClosed() {
        com.fasterxml.jackson.databind.node.ArrayNode arr = MAPPER.createArrayNode();
        arr.add(automationKeyframeNode(num(10), num(30), text("0.5")));
        assertThrows(IllegalArgumentException.class,
                () -> AutomationCanonicalSemantics.fromCanonicalValue("a-1", automationNode(arr)));
    }

    @Test
    void automationValidStrictPayloadRoundtripsExactly() {
        com.fasterxml.jackson.databind.node.ArrayNode arr = MAPPER.createArrayNode();
        arr.add(automationKeyframeNode(num(10), num(30), numD(0.5)));
        ObjectNode node = (ObjectNode) automationNode(arr);
        JsonNode canonical = AutomationCanonicalSemantics.canonicalValue(
                AutomationCanonicalSemantics.fromCanonicalValue("a-1", node));
        // canonicalValue of a single keyframe curve → keyframe time preserved
        assertEquals(1, canonical.path("keyframes").path(0).path("timeTicks").asLong());
        assertEquals(3, canonical.path("keyframes").path(0).path("timeTimeScale").asLong());
        assertEquals(0.5, canonical.path("keyframes").path(0).path("value").asDouble());
    }
}

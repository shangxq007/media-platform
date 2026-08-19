package com.example.platform.timeline.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve;
import com.example.platform.timeline.canonicalmodel.CanonicalTransition;
import com.example.platform.timeline.semantics.automation.AutomationCanonicalSemantics;
import com.example.platform.timeline.semantics.transition.TransitionCanonicalSemantics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * R5-A (CHECKPOINT_A Round 5): STRICT fail-closed decode — the Transition /
 * Automation canonical decoders must NEVER synthesize authored semantics.
 * Missing or malformed REQUIRED authored fields → IllegalArgumentException.
 * No implicit version/mediaType/alignment/temporalPolicy/timeScale, no
 * synthetic kf_N ids, no synthetic valueType/extrapolation/interpolation/0.0.
 */
class CheckpointARound5StrictDecodeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── canonical Transition payload builders ─────────────────────────────

    private static ObjectNode validTransition() {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("transitionDefinitionId", "def-1");
        n.put("transitionDefinitionVersion", "2.0");
        n.put("outgoingClipId", "clip-a");
        n.put("incomingClipId", "clip-b");
        n.put("mediaType", "VIDEO");
        n.put("durationTicks", 15L);
        n.put("durationTimeScale", 30L);
        n.put("alignment", "CENTER_ON_CUT");
        n.put("temporalPolicy", "USE_SOURCE_HANDLES");
        n.set("parameters", MAPPER.createObjectNode());
        return n;
    }

    private static ObjectNode without(ObjectNode node, String field) {
        ObjectNode copy = node.deepCopy();
        copy.remove(field);
        return copy;
    }

    private static String json(ObjectNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ── TRANSITION: every REQUIRED authored field individually removed → FAIL CLOSED ──

    @Test
    void transitionMissingDefinitionIdFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCanonicalSemantics.fromCanonicalJson("tr-1",
                        json(without(validTransition(), "transitionDefinitionId"))),
                "missing transitionDefinitionId must fail closed");
    }

    @Test
    void transitionMissingDefinitionVersionFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCanonicalSemantics.fromCanonicalJson("tr-1",
                        json(without(validTransition(), "transitionDefinitionVersion"))),
                "missing transitionDefinitionVersion must fail closed (no '1.0' synthesis)");
    }

    @Test
    void transitionMissingOutgoingClipFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCanonicalSemantics.fromCanonicalJson("tr-1",
                        json(without(validTransition(), "outgoingClipId"))),
                "missing outgoingClipId must fail closed");
    }

    @Test
    void transitionMissingIncomingClipFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCanonicalSemantics.fromCanonicalJson("tr-1",
                        json(without(validTransition(), "incomingClipId"))),
                "missing incomingClipId must fail closed");
    }

    @Test
    void transitionMissingMediaTypeFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCanonicalSemantics.fromCanonicalJson("tr-1",
                        json(without(validTransition(), "mediaType"))),
                "missing mediaType must fail closed (no 'VIDEO' synthesis)");
    }

    @Test
    void transitionMissingDurationFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCanonicalSemantics.fromCanonicalJson("tr-1",
                        json(without(validTransition(), "durationTicks"))),
                "missing durationTicks must fail closed");
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCanonicalSemantics.fromCanonicalJson("tr-1",
                        json(without(validTransition(), "durationTimeScale"))),
                "missing durationTimeScale must fail closed (no implicit timeScale=1)");
    }

    @Test
    void transitionMalformedDurationFailsClosed() {
        ObjectNode n = validTransition();
        n.put("durationTicks", 0L); // non-positive duration
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCanonicalSemantics.fromCanonicalJson("tr-1", json(n)),
                "zero duration must fail closed");
        ObjectNode n2 = validTransition();
        n2.put("durationTimeScale", 0L);
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCanonicalSemantics.fromCanonicalJson("tr-1", json(n2)),
                "zero timeScale must fail closed");
    }

    @Test
    void transitionMissingAlignmentAndPolicyFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCanonicalSemantics.fromCanonicalJson("tr-1",
                        json(without(validTransition(), "alignment"))),
                "missing alignment must fail closed (no 'CENTER_ON_CUT' synthesis)");
        assertThrows(IllegalArgumentException.class,
                () -> TransitionCanonicalSemantics.fromCanonicalJson("tr-1",
                        json(without(validTransition(), "temporalPolicy"))),
                "missing temporalPolicy must fail closed (no 'USE_SOURCE_HANDLES' synthesis)");
    }

    @Test
    void transitionValidPayloadRoundTripsExactDomainValue() {
        CanonicalTransition decoded = TransitionCanonicalSemantics.fromCanonicalJson(
                "tr-1", json(validTransition()));
        assertEquals("def-1", decoded.transitionDefinitionId());
        assertEquals("2.0", decoded.transitionDefinitionVersion(), "authored version preserved (no default)");
        assertEquals(MediaTime.ofTicks(15, 30), decoded.duration(), "exact rational duration");
        assertEquals("CENTER_ON_CUT", decoded.alignment());
        // identity (Timeline-owned) preserved from the caller
        assertEquals("tr-1", decoded.transitionId());
        // canonical value → encode → decode is lossless
        CanonicalTransition again = TransitionCanonicalSemantics.fromCanonicalJson("tr-1",
                TransitionCanonicalSemantics.encode(decoded));
        assertEquals(decoded, again, "encode/decode round-trip must be lossless");
    }

    @Test
    void transitionEveryFieldParticipatesInFingerprint() {
        CanonicalTransition base = TransitionCanonicalSemantics.fromCanonicalJson("tr-1", json(validTransition()));
        ObjectNode twin = validTransition();
        twin.put("mediaType", "AUDIO");
        CanonicalTransition modified = TransitionCanonicalSemantics.fromCanonicalJson("tr-1", json(twin));
        assertNotEquals(TransitionCanonicalSemantics.semanticFingerprint(base),
                TransitionCanonicalSemantics.semanticFingerprint(modified),
                "mediaType change must change the fingerprint");
        ObjectNode twin2 = validTransition();
        twin2.put("alignment", "START_ON_CUT");
        CanonicalTransition modified2 = TransitionCanonicalSemantics.fromCanonicalJson("tr-1", json(twin2));
        assertNotEquals(TransitionCanonicalSemantics.semanticFingerprint(base),
                TransitionCanonicalSemantics.semanticFingerprint(modified2),
                "alignment change must change the fingerprint");
    }

    @Test
    void transitionDomainValueIsTheContractNotSnapshot() {
        // The authority's canonical contract is defined over CanonicalTransition
        // (domain value); the diff snapshot is only merge transport.
        CanonicalTransition domain = TransitionCanonicalSemantics.fromCanonicalJson("tr-1", json(validTransition()));
        var snapshot = TransitionCanonicalSemantics.toSnapshotValue(domain);
        assertEquals(domain, TransitionCanonicalSemantics.fromSnapshotValue(snapshot),
                "snapshot projection must round-trip exactly back to the domain value");
    }

    // ── AUTOMATION: every REQUIRED authored field individually removed → FAIL CLOSED ──

    private static ObjectNode validAutomation() {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("targetEntityId", "clip-a");
        n.put("parameterPath", "effects.blur.radius");
        n.put("valueType", "float");
        n.put("extrapolation", "HOLD");
        ObjectNode kf = MAPPER.createObjectNode();
        kf.put("keyframeId", "kf-1");
        kf.put("timeTicks", 15L);
        kf.put("timeTimeScale", 30L);
        kf.put("value", 0.5);
        kf.put("interpolation", "LINEAR");
        n.set("keyframes", MAPPER.createArrayNode().add(kf));
        return n;
    }

    @Test
    void automationMissingTargetEntityFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> AutomationCanonicalSemantics.fromCanonicalJson("auto-1",
                        json(without(validAutomation(), "targetEntityId"))),
                "missing targetEntityId must fail closed");
    }

    @Test
    void automationMissingParameterPathFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> AutomationCanonicalSemantics.fromCanonicalJson("auto-1",
                        json(without(validAutomation(), "parameterPath"))),
                "missing parameterPath must fail closed");
    }

    @Test
    void automationMissingValueTypeFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> AutomationCanonicalSemantics.fromCanonicalJson("auto-1",
                        json(without(validAutomation(), "valueType"))),
                "missing valueType must fail closed (no 'float' synthesis)");
    }

    @Test
    void automationMissingExtrapolationFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> AutomationCanonicalSemantics.fromCanonicalJson("auto-1",
                        json(without(validAutomation(), "extrapolation"))),
                "missing extrapolation must fail closed (no 'HOLD' synthesis)");
    }

    @Test
    void automationMissingKeyframesFieldFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> AutomationCanonicalSemantics.fromCanonicalJson("auto-1",
                        json(without(validAutomation(), "keyframes"))),
                "missing keyframes field must fail closed (not silently zero keyframes)");
    }

    @Test
    void automationEmptyKeyframesArrayIsValid() {
        ObjectNode n = validAutomation();
        n.set("keyframes", MAPPER.createArrayNode());
        CanonicalAutomationCurve decoded = AutomationCanonicalSemantics.fromCanonicalJson("auto-1", json(n));
        assertTrue(decoded.keyframes().isEmpty(),
                "explicitly empty keyframes array is a valid zero-keyframe curve");
    }

    @Test
    void automationMissingKeyframeIdFailsClosed() {
        ObjectNode n = validAutomation();
        ObjectNode kf = (ObjectNode) n.path("keyframes").get(0);
        kf.remove("keyframeId");
        assertThrows(IllegalArgumentException.class,
                () -> AutomationCanonicalSemantics.fromCanonicalJson("auto-1", json(n)),
                "missing keyframeId must fail closed (no kf_N synthesis)");
    }

    @Test
    void automationMissingKeyframeTimeFailsClosed() {
        ObjectNode n = validAutomation();
        ObjectNode kf = (ObjectNode) n.path("keyframes").get(0);
        kf.remove("timeTicks");
        assertThrows(IllegalArgumentException.class,
                () -> AutomationCanonicalSemantics.fromCanonicalJson("auto-1", json(n)),
                "missing timeTicks must fail closed");
        ObjectNode n2 = validAutomation();
        ((ObjectNode) n2.path("keyframes").get(0)).remove("timeTimeScale");
        assertThrows(IllegalArgumentException.class,
                () -> AutomationCanonicalSemantics.fromCanonicalJson("auto-1", json(n2)),
                "missing timeTimeScale must fail closed (no implicit timeScale=1)");
    }

    @Test
    void automationMissingKeyframeValueFailsClosed() {
        ObjectNode n = validAutomation();
        ((ObjectNode) n.path("keyframes").get(0)).remove("value");
        assertThrows(IllegalArgumentException.class,
                () -> AutomationCanonicalSemantics.fromCanonicalJson("auto-1", json(n)),
                "missing keyframe value must fail closed (no 0.0 synthesis)");
    }

    @Test
    void automationMissingInterpolationFailsClosed() {
        ObjectNode n = validAutomation();
        ((ObjectNode) n.path("keyframes").get(0)).remove("interpolation");
        assertThrows(IllegalArgumentException.class,
                () -> AutomationCanonicalSemantics.fromCanonicalJson("auto-1", json(n)),
                "missing interpolation must fail closed (no 'LINEAR' synthesis)");
    }

    @Test
    void automationValidPayloadRoundTripsExactValues() {
        CanonicalAutomationCurve decoded = AutomationCanonicalSemantics.fromCanonicalJson(
                "auto-1", json(validAutomation()));
        assertEquals("effects.blur.radius", decoded.parameterPath());
        assertEquals("float", decoded.valueType());
        assertEquals("HOLD", decoded.extrapolation());
        assertEquals(MediaTime.ofTicks(15, 30), decoded.keyframes().get(0).time(),
                "exact MediaTime preserved");
        assertEquals(0.5, decoded.keyframes().get(0).value());
        assertEquals("LINEAR", decoded.keyframes().get(0).interpolation());
        assertEquals("kf-1", decoded.keyframes().get(0).keyframeId(), "authored id preserved");
        CanonicalAutomationCurve again = AutomationCanonicalSemantics.fromCanonicalJson("auto-1",
                AutomationCanonicalSemantics.encode(decoded));
        assertEquals(decoded, again, "encode/decode round-trip must be lossless");
    }

    @Test
    void automationDeterministicKeyframeOrdering() {
        // out-of-order keyframes in the payload are deterministically ordered by time
        ObjectNode n = MAPPER.createObjectNode();
        n.put("targetEntityId", "clip-a");
        n.put("parameterPath", "effects.gain");
        n.put("valueType", "float");
        n.put("extrapolation", "HOLD");
        ObjectNode kf2 = MAPPER.createObjectNode();
        kf2.put("keyframeId", "kf-2");
        kf2.put("timeTicks", 30L);
        kf2.put("timeTimeScale", 30L);
        kf2.put("value", 1.0);
        kf2.put("interpolation", "LINEAR");
        ObjectNode kf1 = MAPPER.createObjectNode();
        kf1.put("keyframeId", "kf-1");
        kf1.put("timeTicks", 15L);
        kf1.put("timeTimeScale", 30L);
        kf1.put("value", 0.5);
        kf1.put("interpolation", "LINEAR");
        n.set("keyframes", MAPPER.createArrayNode().add(kf2).add(kf1));
        CanonicalAutomationCurve decoded = AutomationCanonicalSemantics.fromCanonicalJson("auto-1", json(n));
        assertEquals(List.of("kf-1", "kf-2"),
                decoded.keyframes().stream().map(k -> k.keyframeId()).toList(),
                "keyframes must be deterministically ordered by time");
    }
}

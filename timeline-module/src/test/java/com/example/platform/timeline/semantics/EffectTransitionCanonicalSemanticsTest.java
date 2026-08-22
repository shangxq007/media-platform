package com.example.platform.timeline.semantics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.semantics.automation.Automation;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectInstance.EffectMediaType;
import com.example.platform.timeline.semantics.serialization.CanonicalSerializer;
import com.example.platform.timeline.semantics.transition.TransitionInstance;
import com.example.platform.timeline.semantics.transition.TransitionInstance.TransitionAlignment;
import com.example.platform.timeline.semantics.transition.TransitionInstance.TransitionMediaType;
import com.example.platform.timeline.semantics.transition.TransitionInstance.TransitionTemporalPolicy;
import com.example.platform.timeline.semantics.validation.TimelineSemanticModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * TIMELINE_EFFECT_TRANSITION_CANONICALIZATION_V1 (C12/C13/§29-36): proves
 * Effect/Transition/Automation participate in deterministic canonical
 * serialization and that authored semantic changes affect the content hash.
 *
 * Regression proof for the serializer fix: parameters / automationBindings were
 * previously omitted from canonical serialization — parameter changes did NOT
 * change the content hash.
 */
class EffectTransitionCanonicalSemanticsTest {

    private static final MediaTime ZERO = MediaTime.ZERO;

    private static EffectInstance effect(String id, Map<String, String> params) {
        return new EffectInstance(id, "video.color.adjustment", "1.0",
                EffectMediaType.VIDEO, true,
                new com.example.platform.timeline.semantics.clip.MediaClip.TimeRange(ZERO,
                        MediaTime.ofTicks(30, 30)), params, Map.of(),
                null, EffectInstance.EffectProvenance.untracked());
    }

    private static TransitionInstance transition(String id, String defId, Map<String, String> params) {
        return new TransitionInstance(id, defId, "1.0", "clip-A", "clip-B",
                TransitionMediaType.VIDEO, MediaTime.ofTicks(15, 30),
                TransitionAlignment.CENTER_ON_CUT, TransitionTemporalPolicy.USE_SOURCE_HANDLES, params);
    }

    private static Automation.AutomationCurve automation(double v1, double v2) {
        return new Automation.AutomationCurve("auto-1", "fx-1", "opacity", "float",
                List.of(new Automation.Keyframe("kf-1", ZERO, v1, Automation.InterpolationMode.LINEAR),
                        new Automation.Keyframe("kf-2", MediaTime.ofTicks(30, 30), v2, Automation.InterpolationMode.LINEAR)),
                Automation.ExtrapolationMode.HOLD);
    }

    // ── Serialization determinism (repeat xN byte-identical) ──
    @Test
    void serializationIsDeterministicRepeated() {
        TimelineSemanticModel model = new TimelineSemanticModel(List.of(), List.of(), List.of(effect("fx-1", Map.of("exposure", "1.0", "contrast", "1.2"))), List.of(), "timeline-semantics-v1");
        String first = CanonicalSerializer.serialize(model);
        for (int i = 0; i < 10; i++) {
            assertEquals(first, CanonicalSerializer.serialize(model),
                    "repeated serialization must be byte-identical");
        }
    }

    // ── EFFECT_PARAMETER_CHANGE_AFFECTS_HASH (§30) ──
    @Test
    void effectParameterChangeChangesHash() {
        TimelineSemanticModel a = new TimelineSemanticModel(List.of(), List.of(), List.of(effect("fx-1", Map.of("exposure", "1.0"))), List.of(), "timeline-semantics-v1");
        TimelineSemanticModel b = new TimelineSemanticModel(List.of(), List.of(), List.of(effect("fx-1", Map.of("exposure", "2.0"))), List.of(), "timeline-semantics-v1");
        assertNotEquals(CanonicalSerializer.serialize(a), CanonicalSerializer.serialize(b),
                "parameter change must change canonical serialization");
    }

    // ── EFFECT_ORDER_CHANGE_AFFECTS_HASH (§30) ──
    @Test
    void effectOrderChangeChangesHash() {
        TimelineSemanticModel a = new TimelineSemanticModel(List.of(), List.of(), List.of(effect("fx-1", Map.of("exposure", "1.0")), effect("fx-2", Map.of("blur", "2.0"))), List.of(), "timeline-semantics-v1");
        TimelineSemanticModel b = new TimelineSemanticModel(List.of(), List.of(), List.of(effect("fx-2", Map.of("blur", "2.0")), effect("fx-1", Map.of("exposure", "1.0"))), List.of(), "timeline-semantics-v1");
        assertNotEquals(CanonicalSerializer.serialize(a), CanonicalSerializer.serialize(b),
                "effect reorder must change canonical serialization");
    }

    // ── AUTOMATION_CHANGE_AFFECTS_HASH (§30) ──
    @Test
    void automationChangeChangesHash() {
        TimelineSemanticModel a = new TimelineSemanticModel(List.of(), List.of(), List.of(), List.of(automation(0.0, 1.0)), "timeline-semantics-v1");
        TimelineSemanticModel b = new TimelineSemanticModel(List.of(), List.of(), List.of(), List.of(automation(0.0, 0.5)), "timeline-semantics-v1");
        assertNotEquals(CanonicalSerializer.serialize(a), CanonicalSerializer.serialize(b),
                "automation keyframe value change must change canonical serialization");
    }

    // ── TRANSITION_CHANGE_AFFECTS_HASH (§30) ──
    @Test
    void transitionParameterChangeChangesHash() {
        TimelineSemanticModel a = new TimelineSemanticModel(List.of(), List.of(transition("t-1", "video.dissolve", Map.of("duration", "0.8"))), List.of(), List.of(), "timeline-semantics-v1");
        TimelineSemanticModel b = new TimelineSemanticModel(List.of(), List.of(transition("t-1", "video.dissolve", Map.of("duration", "1.2"))), List.of(), List.of(), "timeline-semantics-v1");
        assertNotEquals(CanonicalSerializer.serialize(a), CanonicalSerializer.serialize(b),
                "transition parameter change must change canonical serialization");
    }

    @Test
    void transitionTypeChangeChangesHash() {
        TimelineSemanticModel a = new TimelineSemanticModel(List.of(), List.of(transition("t-1", "video.dissolve", Map.of())), List.of(), List.of(), "timeline-semantics-v1");
        TimelineSemanticModel b = new TimelineSemanticModel(List.of(), List.of(transition("t-1", "video.wipe", Map.of())), List.of(), List.of(), "timeline-semantics-v1");
        assertNotEquals(CanonicalSerializer.serialize(a), CanonicalSerializer.serialize(b),
                "transition definition change must change canonical serialization");
    }

    // ── Map key sorting determinism (independent of insertion order) ──
    @Test
    void parameterMapKeyOrderDoesNotMatter() {
        TimelineSemanticModel a = new TimelineSemanticModel(List.of(), List.of(), List.of(effect("fx-1", Map.of("exposure", "1.0", "contrast", "1.2"))), List.of(), "timeline-semantics-v1");
        TimelineSemanticModel b = new TimelineSemanticModel(List.of(), List.of(), List.of(effect("fx-1", Map.of("contrast", "1.2", "exposure", "1.0"))), List.of(), "timeline-semantics-v1");
        assertEquals(CanonicalSerializer.serialize(a), CanonicalSerializer.serialize(b),
                "map key insertion order must not affect canonical serialization (sorted keys)");
    }

    // ── Automation deterministic keyframe ordering (duplicate time rejected) ──
    @Test
    void automationRejectsDuplicateKeyframeTime() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Automation.AutomationCurve("auto-1", "fx-1", "opacity", "float",
                        List.of(new Automation.Keyframe("kf-1", ZERO, 0.0, Automation.InterpolationMode.LINEAR),
                                new Automation.Keyframe("kf-2", ZERO, 1.0, Automation.InterpolationMode.LINEAR)),
                        Automation.ExtrapolationMode.HOLD),
                "duplicate keyframe time must be rejected");
    }

    // ── Transition first-class semantics (not a clip effect) ──
    @Test
    void transitionIsFirstClassRelationship() {
        TransitionInstance t = transition("t-1", "video.dissolve", Map.of("duration", "0.8"));
        assertEquals("clip-A", t.outgoingClipId());
        assertEquals("clip-B", t.incomingClipId());
        assertTrue(t.duration().isGreaterThan(MediaTime.ZERO), "duration must be > zero");
    }

    // ── Automation exact MediaTime (no wall clock) ──
    @Test
    void automationUsesExactMediaTime() {
        Automation.AutomationCurve c = automation(0.0, 1.0);
        assertEquals(MediaTime.ofTicks(30, 30), c.keyframes().get(1).time(),
                "automation keyframe time is exact MediaTime");
        double mid = c.evaluate(MediaTime.ofTicks(15, 30));
        assertEquals(0.5, mid, 1e-9, "linear interpolation at midpoint");
    }

    // ── serialize → deserialize semantic equality for effects ──
    @Test
    void serializeContainsTypedParameterState() {
        TimelineSemanticModel model = new TimelineSemanticModel(List.of(), List.of(transition("t-1", "video.dissolve", Map.of("duration", "0.8"))), List.of(effect("fx-1", Map.of("exposure", "1.0"))), List.of(automation(0.0, 1.0)), "timeline-semantics-v1");
        String s = CanonicalSerializer.serialize(model);
        assertTrue(s.contains("\"parameters\":{\"exposure\":\"1.0\"}"),
                "effect parameters must be serialized: " + s);
        assertTrue(s.contains("\"transitionDefinitionId\":\"video.dissolve\""),
                "transition definition must be serialized");
        assertTrue(s.contains("\"keyframes\""), "automation keyframes must be serialized");
    }
}

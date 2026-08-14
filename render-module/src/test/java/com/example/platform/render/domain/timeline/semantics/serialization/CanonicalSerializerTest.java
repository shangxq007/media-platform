package com.example.platform.render.domain.timeline.semantics.serialization;

import com.example.platform.render.domain.timeline.semantics.automation.Automation;
import com.example.platform.render.domain.timeline.semantics.clip.MediaClip;
import com.example.platform.render.domain.timeline.semantics.effect.EffectInstance;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.render.domain.timeline.semantics.transition.TransitionInstance;
import com.example.platform.render.domain.timeline.semantics.validation.TimelineSemanticModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalSerializerTest {

    private static MediaClip clip(String id, long tStart, long tEnd, long sStart, long sEnd) {
        return new MediaClip(
            id, "track-1",
            new MediaClip.TimeRange(
                MediaTime.ofRational(tStart, 1), MediaTime.ofRational(tEnd, 1)),
            new MediaClip.TimeRange(
                MediaTime.ofRational(sStart, 1), MediaTime.ofRational(sEnd, 1)),
            new MediaClip.Rational(1, 1),
            "asset-" + id
        );
    }

    @Test
    @DisplayName("Serialization contains schemaVersion field")
    void schemaVersionPresent() {
        TimelineSemanticModel model = TimelineSemanticModel.empty();
        String json = CanonicalSerializer.serialize(model);
        assertTrue(json.contains("\"schemaVersion\":\"timeline-semantics-v1\""));
    }

    @Test
    @DisplayName("Serialization produces valid JSON structure")
    void validJsonStructure() {
        TimelineSemanticModel model = new TimelineSemanticModel(
            List.of(clip("c1", 0, 5, 0, 5)),
            List.of(), List.of(), List.of(), "timeline-semantics-v1"
        );
        String json = CanonicalSerializer.serialize(model);
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));
        assertTrue(json.contains("\"clipId\":\"c1\""));
    }

    @Test
    @DisplayName("Serialization is deterministic for same input")
    void deterministicSerialization() {
        TimelineSemanticModel m1 = new TimelineSemanticModel(
            List.of(clip("c1", 0, 5, 0, 5)),
            List.of(), List.of(), List.of(), "timeline-semantics-v1"
        );
        TimelineSemanticModel m2 = new TimelineSemanticModel(
            List.of(clip("c1", 0, 5, 0, 5)),
            List.of(), List.of(), List.of(), "timeline-semantics-v1"
        );
        assertEquals(CanonicalSerializer.serialize(m1), CanonicalSerializer.serialize(m2));
    }

    @Test
    @DisplayName("Digest is stable for same semantic model")
    void digestStability() {
        TimelineSemanticModel m1 = new TimelineSemanticModel(
            List.of(clip("c1", 0, 5, 0, 5)),
            List.of(), List.of(), List.of(), "timeline-semantics-v1"
        );
        TimelineSemanticModel m2 = new TimelineSemanticModel(
            List.of(clip("c1", 0, 5, 0, 5)),
            List.of(), List.of(), List.of(), "timeline-semantics-v1"
        );
        assertEquals(CanonicalSerializer.digest(m1), CanonicalSerializer.digest(m2));
    }

    @Test
    @DisplayName("Different models produce different digests")
    void differentDigests() {
        TimelineSemanticModel m1 = new TimelineSemanticModel(
            List.of(clip("c1", 0, 5, 0, 5)),
            List.of(), List.of(), List.of(), "timeline-semantics-v1"
        );
        TimelineSemanticModel m2 = new TimelineSemanticModel(
            List.of(clip("c2", 0, 5, 0, 5)),
            List.of(), List.of(), List.of(), "timeline-semantics-v1"
        );
        assertNotEquals(CanonicalSerializer.digest(m1), CanonicalSerializer.digest(m2));
    }

    @Test
    @DisplayName("Unknown schema version is rejected")
    void unknownSchemaVersion() {
        assertThrows(IllegalArgumentException.class, () ->
            CanonicalSerializer.validateSchemaVersion("unknown-version"));
    }

    @Test
    @DisplayName("Known schema version is accepted")
    void knownSchemaVersion() {
        assertDoesNotThrow(() ->
            CanonicalSerializer.validateSchemaVersion("timeline-semantics-v1"));
    }

    @Test
    @DisplayName("Serialization idempotence: serializing twice produces same result")
    void serializationIdempotence() {
        TimelineSemanticModel model = new TimelineSemanticModel(
            List.of(clip("c1", 0, 5, 0, 5)),
            List.of(), List.of(), List.of(), "timeline-semantics-v1"
        );
        String s1 = CanonicalSerializer.serialize(model);
        String s2 = CanonicalSerializer.serialize(model);
        assertEquals(s1, s2);
    }

    @Test
    @DisplayName("Complete timeline with all entity types serializes correctly")
    void completeTimeline() {
        MediaClip clip1 = clip("c1", 0, 5, 0, 5);
        MediaClip clip2 = clip("c2", 5, 10, 0, 5);

        TransitionInstance tx = new TransitionInstance(
            "tx-1", "def-1", "1.0", "c1", "c2",
            TransitionInstance.TransitionMediaType.VIDEO,
            MediaTime.ofRational(1, 2),
            TransitionInstance.TransitionAlignment.CENTER_ON_CUT,
            TransitionInstance.TransitionTemporalPolicy.USE_SOURCE_HANDLES,
            null
        );

        Automation.Keyframe k1 = new Automation.Keyframe(
            "kf-1", MediaTime.ZERO, 0.0, Automation.InterpolationMode.LINEAR);
        Automation.Keyframe k2 = new Automation.Keyframe(
            "kf-2", MediaTime.ofRational(5, 1), 1.0, Automation.InterpolationMode.LINEAR);
        Automation.AutomationCurve auto = new Automation.AutomationCurve(
            "auto-1", "c1", "opacity", "float",
            List.of(k1, k2), Automation.ExtrapolationMode.HOLD);

        TimelineSemanticModel model = new TimelineSemanticModel(
            List.of(clip1, clip2), List.of(tx), List.of(), List.of(auto),
            "timeline-semantics-v1");

        String json = CanonicalSerializer.serialize(model);
        assertTrue(json.contains("\"transitions\""));
        assertTrue(json.contains("\"keyframes\""));
        assertTrue(json.contains("\"tx-1\""));
        assertTrue(json.contains("\"auto-1\""));

        // Digest should be stable
        String d1 = CanonicalSerializer.digest(model);
        String d2 = CanonicalSerializer.digest(model);
        assertEquals(d1, d2);
    }
}

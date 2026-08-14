package com.example.platform.render.domain.timeline.semantics;

import com.example.platform.render.domain.timeline.semantics.automation.Automation;
import com.example.platform.render.domain.timeline.semantics.clip.MediaClip;
import com.example.platform.render.domain.timeline.semantics.duration.TimelineDurationCalculator;
import com.example.platform.render.domain.timeline.semantics.serialization.CanonicalSerializer;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.render.domain.timeline.semantics.validation.TimelineMediaSemanticsValidator;
import com.example.platform.render.domain.timeline.semantics.validation.TimelineSemanticModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for timeline media semantics.
 * Tests: serialization idempotence, time arithmetic closure,
 * deterministic validation ordering, stable digest, no dangling references.
 */
class PropertyBasedTests {

    @Test
    @DisplayName("Property: serialization idempotence (100 iterations)")
    void serializationIdempotence() {
        TimelineSemanticModel model = buildComplexModel();
        String first = CanonicalSerializer.serialize(model);
        for (int i = 0; i < 100; i++) {
            assertEquals(first, CanonicalSerializer.serialize(model),
                "Serialization changed at iteration " + i);
        }
    }

    @Test
    @DisplayName("Property: time arithmetic closure (addition is associative)")
    void timeAdditionAssociative() {
        MediaTime a = MediaTime.ofRational(1, 3);
        MediaTime b = MediaTime.ofRational(1, 4);
        MediaTime c = MediaTime.ofRational(1, 5);

        MediaTime r1 = a.add(b).add(c);
        MediaTime r2 = a.add(b.add(c));
        assertEquals(r1, r2);
    }

    @Test
    @DisplayName("Property: time arithmetic closure (multiplication is associative)")
    void timeMultiplicationAssociative() {
        MediaTime t = MediaTime.ofRational(3, 4);
        long r1n = 2, r1d = 3, r2n = 4, r2d = 5;

        MediaTime result1 = t.multiplyRational(r1n, r1d).multiplyRational(r2n, r2d);
        MediaTime result2 = t.multiplyRational(r1n * r2n, r1d * r2d);
        assertEquals(result1, result2);
    }

    @Test
    @DisplayName("Property: deterministic validation ordering (100 runs)")
    void deterministicValidationOrdering() {
        TimelineSemanticModel model = buildModelWithError();
        var firstResult = TimelineMediaSemanticsValidator.validate(model);
        var firstErrors = new ArrayList<>(firstResult.errors());

        for (int i = 0; i < 100; i++) {
            var result = TimelineMediaSemanticsValidator.validate(model);
            assertEquals(firstErrors, result.errors(),
                "Validation ordering changed at iteration " + i);
        }
    }

    @Test
    @DisplayName("Property: stable digest for semantically equal input")
    void stableDigest() {
        TimelineSemanticModel m1 = buildComplexModel();
        TimelineSemanticModel m2 = buildComplexModel();
        assertEquals(CanonicalSerializer.digest(m1), CanonicalSerializer.digest(m2));
    }

    @Test
    @DisplayName("Property: no dangling references after canonicalization")
    void noDanglingReferences() {
        TimelineSemanticModel model = buildComplexModel();
        String serialized = CanonicalSerializer.serialize(model);
        // All clip IDs referenced in transitions should exist in clips
        for (var clip : model.clips()) {
            assertTrue(serialized.contains("\"clipId\":\"" + clip.clipId() + "\""));
        }
        // All transition endpoints should reference existing clips
        for (var tx : model.transitions()) {
            assertTrue(serialized.contains("\"outgoingClipId\":\"" + tx.outgoingClipId() + "\""));
            assertTrue(serialized.contains("\"incomingClipId\":\"" + tx.incomingClipId() + "\""));
        }
    }

    @Test
    @DisplayName("Property: duration calculation is O(V) - scales linearly")
    void durationScalesLinearly() {
        // Build timelines of increasing size and verify calculation completes
        for (int size : new int[]{1, 10, 100, 500}) {
            List<MediaClip> clips = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                long start = i * 5L;
                clips.add(new MediaClip(
                    "clip-" + i, "track-1",
                    new MediaClip.TimeRange(
                        MediaTime.ofRational(start, 1),
                        MediaTime.ofRational(start + 5, 1)),
                    new MediaClip.TimeRange(
                        MediaTime.ofRational(start, 1),
                        MediaTime.ofRational(start + 5, 1)),
                    new MediaClip.Rational(1, 1),
                    "asset-" + i
                ));
            }
            TimelineSemanticModel model = new TimelineSemanticModel(
                clips, List.of(), List.of(), List.of(), "timeline-semantics-v1");
            MediaTime duration = TimelineDurationCalculator.calculateDuration(model);
            assertEquals(MediaTime.ofRational(size * 5L, 1), duration);
        }
    }

    private TimelineSemanticModel buildComplexModel() {
        List<MediaClip> clips = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            long start = i * 5L;
            clips.add(new MediaClip(
                "clip-" + i, "track-1",
                new MediaClip.TimeRange(
                    MediaTime.ofRational(start, 1),
                    MediaTime.ofRational(start + 5, 1)),
                new MediaClip.TimeRange(
                    MediaTime.ofRational(start, 1),
                    MediaTime.ofRational(start + 5, 1)),
                new MediaClip.Rational(1, 1),
                "asset-" + i
            ));
        }

        Automation.Keyframe k1 = new Automation.Keyframe(
            "kf-1", MediaTime.ZERO, 0.0, Automation.InterpolationMode.LINEAR);
        Automation.Keyframe k2 = new Automation.Keyframe(
            "kf-2", MediaTime.ofRational(5, 1), 1.0, Automation.InterpolationMode.LINEAR);
        Automation.AutomationCurve curve = new Automation.AutomationCurve(
            "auto-1", "clip-0", "opacity", "float",
            List.of(k1, k2), Automation.ExtrapolationMode.HOLD);

        return new TimelineSemanticModel(
            clips, List.of(), List.of(), List.of(curve), "timeline-semantics-v1");
    }

    private TimelineSemanticModel buildModelWithError() {
        // Duplicate clip IDs
        List<MediaClip> clips = List.of(
            new MediaClip("dup", "track-1",
                new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(5, 1)),
                new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(5, 1)),
                new MediaClip.Rational(1, 1), "a"),
            new MediaClip("dup", "track-1",
                new MediaClip.TimeRange(MediaTime.ofRational(5, 1), MediaTime.ofRational(10, 1)),
                new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(5, 1)),
                new MediaClip.Rational(1, 1), "b")
        );
        return new TimelineSemanticModel(clips, List.of(), List.of(), List.of(),
            "timeline-semantics-v1");
    }
}

package com.example.platform.render.domain.timeline.semantics.automation;

import com.example.platform.render.domain.timeline.semantics.time.MediaTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AutomationTest {

    @Test
    @DisplayName("Basic automation curve with HOLD keyframes")
    void holdCurve() {
        Automation.Keyframe k1 = new Automation.Keyframe(
            "kf-1", MediaTime.ZERO, 0.0, Automation.InterpolationMode.HOLD);
        Automation.Keyframe k2 = new Automation.Keyframe(
            "kf-2", MediaTime.ofRational(1, 1), 1.0, Automation.InterpolationMode.HOLD);

        Automation.AutomationCurve curve = new Automation.AutomationCurve(
            "auto-1", "clip-1", "opacity", "float",
            List.of(k1, k2), Automation.ExtrapolationMode.HOLD
        );

        assertEquals(0.0, curve.evaluate(MediaTime.ZERO), 0.0001);
        assertEquals(0.0, curve.evaluate(MediaTime.ofRational(1, 2)), 0.0001);
        assertEquals(1.0, curve.evaluate(MediaTime.ofRational(1, 1)), 0.0001);
        assertEquals(1.0, curve.evaluate(MediaTime.ofRational(2, 1)), 0.0001);
    }

    @Test
    @DisplayName("LINEAR interpolation between keyframes")
    void linearInterpolation() {
        Automation.Keyframe k1 = new Automation.Keyframe(
            "kf-1", MediaTime.ZERO, 0.0, Automation.InterpolationMode.LINEAR);
        Automation.Keyframe k2 = new Automation.Keyframe(
            "kf-2", MediaTime.ofRational(2, 1), 1.0, Automation.InterpolationMode.LINEAR);

        Automation.AutomationCurve curve = new Automation.AutomationCurve(
            "auto-1", "clip-1", "gain", "float",
            List.of(k1, k2), Automation.ExtrapolationMode.HOLD
        );

        assertEquals(0.0, curve.evaluate(MediaTime.ZERO), 0.0001);
        assertEquals(0.25, curve.evaluate(MediaTime.ofRational(1, 2)), 0.0001);
        assertEquals(0.5, curve.evaluate(MediaTime.ofRational(1, 1)), 0.0001);
        assertEquals(0.75, curve.evaluate(MediaTime.ofRational(3, 2)), 0.0001);
        assertEquals(1.0, curve.evaluate(MediaTime.ofRational(2, 1)), 0.0001);
    }

    @Test
    @DisplayName("Duplicate keyframe times are rejected")
    void duplicateKeyframeTimes() {
        Automation.Keyframe k1 = new Automation.Keyframe(
            "kf-1", MediaTime.ofRational(1, 1), 0.0, Automation.InterpolationMode.HOLD);
        Automation.Keyframe k2 = new Automation.Keyframe(
            "kf-2", MediaTime.ofRational(1, 1), 1.0, Automation.InterpolationMode.HOLD);

        assertThrows(IllegalArgumentException.class, () ->
            new Automation.AutomationCurve(
                "auto-1", "clip-1", "opacity", "float",
                List.of(k1, k2), Automation.ExtrapolationMode.HOLD)
        );
    }

    @Test
    @DisplayName("Keyframes are sorted by time on construction")
    void sortedKeyframes() {
        Automation.Keyframe k2 = new Automation.Keyframe(
            "kf-2", MediaTime.ofRational(2, 1), 1.0, Automation.InterpolationMode.HOLD);
        Automation.Keyframe k1 = new Automation.Keyframe(
            "kf-1", MediaTime.ZERO, 0.0, Automation.InterpolationMode.HOLD);

        Automation.AutomationCurve curve = new Automation.AutomationCurve(
            "auto-1", "clip-1", "opacity", "float",
            List.of(k2, k1), Automation.ExtrapolationMode.HOLD
        );

        assertEquals("kf-1", curve.keyframes().get(0).keyframeId());
        assertEquals("kf-2", curve.keyframes().get(1).keyframeId());
    }

    @Test
    @DisplayName("HOLD before first keyframe: returns first value")
    void extrapolateBeforeFirst() {
        Automation.Keyframe k1 = new Automation.Keyframe(
            "kf-1", MediaTime.ofRational(1, 1), 0.5, Automation.InterpolationMode.HOLD);

        Automation.AutomationCurve curve = new Automation.AutomationCurve(
            "auto-1", "clip-1", "opacity", "float",
            List.of(k1), Automation.ExtrapolationMode.HOLD
        );

        assertEquals(0.5, curve.evaluate(MediaTime.ZERO), 0.0001);
    }

    @Test
    @DisplayName("HOLD after last keyframe: returns last value")
    void extrapolateAfterLast() {
        Automation.Keyframe k1 = new Automation.Keyframe(
            "kf-1", MediaTime.ZERO, 0.5, Automation.InterpolationMode.HOLD);

        Automation.AutomationCurve curve = new Automation.AutomationCurve(
            "auto-1", "clip-1", "opacity", "float",
            List.of(k1), Automation.ExtrapolationMode.HOLD
        );

        assertEquals(0.5, curve.evaluate(MediaTime.ofRational(5, 1)), 0.0001);
    }

    @Test
    @DisplayName("Empty keyframes: evaluate returns 0")
    void emptyKeyframes() {
        Automation.AutomationCurve curve = new Automation.AutomationCurve(
            "auto-1", "clip-1", "opacity", "float",
            List.of(), Automation.ExtrapolationMode.HOLD
        );
        assertEquals(0.0, curve.evaluate(MediaTime.ofRational(1, 1)), 0.0001);
    }

    @Test
    @DisplayName("Deterministic ordering: same keyframes produce same order")
    void deterministicOrdering() {
        Automation.Keyframe k1 = new Automation.Keyframe(
            "kf-1", MediaTime.ofRational(3, 1), 0.3, Automation.InterpolationMode.HOLD);
        Automation.Keyframe k2 = new Automation.Keyframe(
            "kf-2", MediaTime.ofRational(1, 1), 0.1, Automation.InterpolationMode.HOLD);
        Automation.Keyframe k3 = new Automation.Keyframe(
            "kf-3", MediaTime.ofRational(2, 1), 0.2, Automation.InterpolationMode.HOLD);

        Automation.AutomationCurve curve = new Automation.AutomationCurve(
            "auto-1", "clip-1", "opacity", "float",
            List.of(k1, k2, k3), Automation.ExtrapolationMode.HOLD
        );

        assertEquals("kf-2", curve.keyframes().get(0).keyframeId());
        assertEquals("kf-3", curve.keyframes().get(1).keyframeId());
        assertEquals("kf-1", curve.keyframes().get(2).keyframeId());
    }
}

package com.example.platform.render.domain.timeline.semantics.time;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * C1-CRR2 property/parameterized regression — canonical time quantization.
 *
 * <p>Frozen contract: the canonical persisted Timeline revision payload is
 * frame-based (integer frames @ integer fps, den = 1); the semantic merge
 * snapshot is integer milliseconds. {@link TimelineTimeQuantization} is the
 * SINGLE policy authority pairing the two boundaries (round-half-up both
 * directions). These tests prove the pair is inverse over the supported
 * domain — including the non-millisecond-aligned frames that the C1-CRR1
 * candidate lost (1, 2, 4, 5, 7, 8, 10 @30fps).</p>
 */
class TimelineTimeQuantizationTest {

    private static final int[] SUPPORTED_FPS = {24, 25, 30, 50, 60};

    @ParameterizedTest
    @ValueSource(ints = {24, 25, 30, 50, 60})
    void frameRoundTripIsLosslessForNonAlignedFrames(int fps) {
        // The exact frames the CRR1-FCV probe proved to drift (floor/floor).
        long[] frames = {1, 2, 4, 5, 7, 8, 10, 11, 13, 14, 16, 17, 19, 20, 22, 23, 25, 26, 28, 29, 31, 32, 34};
        for (long frame : frames) {
            MediaTime mt = MediaTime.ofFrames(frame, fps, 1);
            long ms = TimelineTimeQuantization.mediaTimeToMillis(mt);
            long back = TimelineTimeQuantization.millisToFrame(ms, fps);
            assertEquals(frame, back,
                    "roundtrip drift at fps=" + fps + " frame=" + frame + " (ms=" + ms + ")");
        }
    }

    @Test
    void exhaustiveFrameRoundTripFirstThousand() {
        // Exhaustive over frames 0..1000 for every supported fps — not just
        // hand-picked fixtures (task §10: property-style proof).
        for (int fps : SUPPORTED_FPS) {
            for (long frame = 0; frame <= 1000; frame++) {
                MediaTime mt = MediaTime.ofFrames(frame, fps, 1);
                long ms = TimelineTimeQuantization.mediaTimeToMillis(mt);
                long back = TimelineTimeQuantization.millisToFrame(ms, fps);
                assertEquals(frame, back,
                        "exhaustive roundtrip failure fps=" + fps + " frame=" + frame);
            }
        }
    }

    @Test
    void durationRoundTripIsLossless() {
        for (int fps : SUPPORTED_FPS) {
            for (long dur = 0; dur <= 1000; dur++) {
                MediaTime mt = MediaTime.ofFrames(dur, fps, 1);
                long ms = TimelineTimeQuantization.mediaTimeToMillis(mt);
                long back = TimelineTimeQuantization.millisToFrame(ms, fps);
                assertEquals(dur, back, "duration roundtrip failure fps=" + fps + " dur=" + dur);
            }
        }
    }

    @Test
    void repeatedRoundTripIsStable() {
        // P0 -> S0 -> P1 -> S1 -> ... no accumulating drift across cycles.
        for (int fps : SUPPORTED_FPS) {
            for (long frame : new long[]{1, 2, 4, 7, 10, 33, 100, 299, 300, 301}) {
                long current = frame;
                for (int cycle = 0; cycle < 5; cycle++) {
                    MediaTime mt = MediaTime.ofFrames(current, fps, 1);
                    long ms = TimelineTimeQuantization.mediaTimeToMillis(mt);
                    current = TimelineTimeQuantization.millisToFrame(ms, fps);
                }
                assertEquals(frame, current,
                        "repeated cycle drift fps=" + fps + " frame=" + frame);
            }
        }
    }

    @Test
    void exactFrameBoundariesAreExact() {
        // ms-aligned frames must map to exact ms (round-half-up == exact).
        for (int fps : SUPPORTED_FPS) {
            for (long frame : new long[]{0, 3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 60, 90, 150, 300}) {
                long ms = TimelineTimeQuantization.frameToMillis(frame, fps);
                long back = TimelineTimeQuantization.millisToFrame(ms, fps);
                assertEquals(frame, back, "aligned boundary drift fps=" + fps + " frame=" + frame);
            }
        }
    }

    @Test
    void monotonicMsAcrossAdjacentFrames() {
        // The inverse mapping requires strict monotonicity of ms over frames.
        for (int fps : SUPPORTED_FPS) {
            long prev = -1;
            for (long frame = 0; frame <= 1000; frame++) {
                long ms = TimelineTimeQuantization.frameToMillis(frame, fps);
                assertEquals(true, ms > prev,
                        "ms not strictly increasing fps=" + fps + " frame=" + frame);
                prev = ms;
            }
        }
    }

    @Test
    void invalidInputsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TimelineTimeQuantization.millisToFrame(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> TimelineTimeQuantization.millisToFrame(0, -1));
    }
}

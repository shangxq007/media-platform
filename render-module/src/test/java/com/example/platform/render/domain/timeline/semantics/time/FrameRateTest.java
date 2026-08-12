package com.example.platform.render.domain.timeline.semantics.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * C1-CNM1-RED behavioral proofs: exact rational FrameRate authority.
 *
 * <p>RED-01: fractional FrameRate denominator preserved end-to-end (equality,
 * normalization, exact comparison; never decimalized, never truncated).</p>
 */
class FrameRateTest {

    @Test
    void canonicalRatesAreExact() {
        assertEquals(FrameRate.of(24, 1), FrameRate.of(24, 1));
        assertEquals(FrameRate.of(25, 1), FrameRate.of(25, 1));
        assertEquals(FrameRate.of(30, 1), FrameRate.of(30, 1));
        assertEquals(FrameRate.of(50, 1), FrameRate.of(50, 1));
        assertEquals(FrameRate.of(60, 1), FrameRate.of(60, 1));
        assertEquals(FrameRate.of(24000, 1001), FrameRate.of(24000, 1001));
        assertEquals(FrameRate.of(30000, 1001), FrameRate.of(30000, 1001));
        assertEquals(FrameRate.of(60000, 1001), FrameRate.of(60000, 1001));
    }

    @Test
    void gcdNormalizationIsCanonical() {
        // 60000/2002 == 30000/1001 (single canonical normalized representation)
        assertEquals(FrameRate.of(30000, 1001), FrameRate.of(60000, 2002));
        assertEquals(30000L, FrameRate.of(60000, 2002).numerator().longValue());
        assertEquals(1001L, FrameRate.of(60000, 2002).denominator());
    }

    @Test
    void denominatorIsNeverDropped() {
        assertEquals(1001L, FrameRate.of(30000, 1001).denominator());
        assertEquals(30000L, FrameRate.of(30000, 1001).numerator().longValue());
        // never decimalized to 29.97 as canonical state
        assertEquals("30000/1001", FrameRate.of(30000, 1001).toString());
    }

    @Test
    void fractionalRateNeverBecomesIntegerFps() {
        // RED-02: 30000/1001 is NOT 29 and NOT 30 — explicit failure
        assertThrows(ArithmeticException.class, () -> FrameRate.of(30000, 1001).intFps());
        assertThrows(ArithmeticException.class, () -> FrameRate.of(30000, 1001).toIntegerExact());
        assertFalse(FrameRate.of(30000, 1001).isInteger());
        assertEquals(24, FrameRate.of(24, 1).intFps());
        assertEquals(30, FrameRate.of(30, 1).intFps());
    }

    @Test
    void exactComparison() {
        assertTrue(FrameRate.of(30000, 1001).compareTo(FrameRate.of(29, 1)) > 0);
        assertTrue(FrameRate.of(24, 1).compareTo(FrameRate.of(25, 1)) < 0);
        assertEquals(0, FrameRate.of(30000, 1001).compareTo(FrameRate.of(60000, 2002)));
    }

    @Test
    void invalidRatesRejected() {
        assertThrows(IllegalArgumentException.class, () -> FrameRate.of(0, 1));
        assertThrows(IllegalArgumentException.class, () -> FrameRate.of(-30, 1));
        assertThrows(IllegalArgumentException.class, () -> FrameRate.of(30, 0));
        assertThrows(IllegalArgumentException.class, () -> FrameRate.of(30, -1));
    }

    @Test
    void toDoubleIsExplicitApproximationOnly() {
        // projection API: allowed for renderer/display; never canonical
        assertEquals(30.0, FrameRate.of(30, 1).toDouble(), 1e-9);
        assertEquals(30000.0 / 1001.0, FrameRate.of(30000, 1001).toDouble(), 1e-9);
        // canonical form remains exact regardless of the projection
        assertEquals("30000/1001", FrameRate.of(30000, 1001).toString());
    }
}
